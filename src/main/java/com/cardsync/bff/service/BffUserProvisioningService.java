package com.cardsync.bff.service;

import com.cardsync.core.config.NimbusAuthClientProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Usuário é global no NimbusAuth (sem app_key próprio) - "cadastrar usuário" na tela de Usuários
 * pode, na prática, ser "conceder acesso ao Cardsync a um usuário que já existe" (criado por
 * outro app Nimbus, ex.: NimbusFlow). O proxy genérico (BffApiClient/BffAdminProxyController)
 * simplesmente repassaria as chamadas cruas, e:
 * <ul>
 *   <li>POST rejeitaria a criação com 409 (USER_USERNAME_ALREADY_EXISTS) mesmo o usuário não
 *       tendo nenhum grupo do Cardsync ainda;</li>
 *   <li>PUT faria replace total dos grupos numa edição, apagando silenciosamente qualquer grupo de
 *       outro app Nimbus que o usuário já tivesse (o multiselect do CardSyncWeb só lista grupos
 *       com appKey=cardsync, ver GroupsApiService.getAll);</li>
 *   <li>as listagens (options/options-filter/search/get-by-id) exporiam o diretório global de
 *       usuários (de qualquer app Nimbus), já que o NimbusAuth não filtra usuário por app_key -
 *       só grupo tem app_key.</li>
 * </ul>
 * Intercepta esses caminhos pra resolver os três problemas - mesmo princípio do
 * AdminUserService do NimbusFlowServer (create/update/list/get/options, todos com o mesmo recorte
 * "só o que pertence ao meu app"), adaptado ao proxy genérico do Cardsync (sem DTOs tipados - só
 * esses fluxos precisam inspecionar o corpo/resposta, então usa ObjectMapper cru em vez de
 * introduzir uma camada de DTOs só por causa disso).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BffUserProvisioningService {

  private static final String CARDSYNC_APP_KEY = "cardsync";

  private final BffAccessTokenService accessTokenService;
  private final NimbusAuthClientProperties nimbusAuthProps;
  private final ObjectMapper objectMapper;

  private final RestClient rest = RestClient.create();

  public ResponseEntity<byte[]> createOrGrantAccess(
      Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    String token = accessTokenService.getValidAccessTokenOrRevoke(auth, req, res);
    byte[] requestBody = readBody(req);
    JsonNode requestJson = parse(requestBody);
    String userName = requestJson.path("userName").asText(null);

    JsonNode existing = userName == null ? null : findExistingUserByUserName(token, userName);

    if (existing == null) {
      return sendJson(HttpMethod.POST, "/api/v1/users", token, requestBody);
    }

    byte[] mergedBody = buildMergedUpdateBody(existing, requestJson);
    String existingId = existing.path("id").asText();
    return sendJson(HttpMethod.PUT, "/api/v1/users/" + existingId, token, mergedBody);
  }

  /**
   * PUT /api/v1/users/{id} faz replace total dos grupos do usuário (ver UserService.update no
   * NimbusAuth) - busca o estado atual, preserva os grupos de fora do Cardsync intactos, funde com
   * a nova seleção (só grupos Cardsync, vindos do formulário).
   */
  public ResponseEntity<byte[]> updatePreservingOtherAppGroups(
      Authentication auth, HttpServletRequest req, HttpServletResponse res, String id) {
    String token = accessTokenService.getValidAccessTokenOrRevoke(auth, req, res);
    byte[] requestBody = readBody(req);
    JsonNode requestJson = parse(requestBody);

    JsonNode current = fetchUserById(token, id);
    if (current == null) {
      // usuário não encontrado (ou falha ao consultar) - deixa o PUT original seguir e o NimbusAuth
      // devolver o 404/erro real, em vez de mascarar com um comportamento diferente aqui.
      return sendJson(HttpMethod.PUT, "/api/v1/users/" + id, token, requestBody);
    }

    Set<String> finalGroupIds = new LinkedHashSet<>();
    for (JsonNode g : current.path("groups")) {
      if (!CARDSYNC_APP_KEY.equals(g.path("appKey").asText(null))) {
        String groupId = g.path("id").asText(null);
        if (groupId != null) {
          finalGroupIds.add(groupId);
        }
      }
    }
    for (JsonNode g : requestJson.path("groupIds")) {
      finalGroupIds.add(g.asText());
    }

    ObjectNode mergedRequest = requestJson.isObject() ? ((ObjectNode) requestJson) : objectMapper.createObjectNode();
    ArrayNode groupIdsNode = mergedRequest.putArray("groupIds");
    finalGroupIds.forEach(groupIdsNode::add);

    byte[] mergedBody;
    try {
      mergedBody = objectMapper.writeValueAsBytes(mergedRequest);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao montar corpo de atualização preservando outros apps", e);
    }

    return sendJson(HttpMethod.PUT, "/api/v1/users/" + id, token, mergedBody);
  }

  /** GET /bff/v1/users/options e /options-filter (mesmo recorte nas duas, ver
   *  BffAdminUsersController.optionsFilter no NimbusFlowServer) - lista leve id/name/userName só
   *  dos usuários com pelo menos um grupo do Cardsync. */
  public ResponseEntity<byte[]> listOptionsScopedToCardsync(
      Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    String token = accessTokenService.getValidAccessTokenOrRevoke(auth, req, res);

    ArrayNode options = objectMapper.createArrayNode();
    for (JsonNode user : searchAllUsersRaw(token)) {
      if (belongsToCardsync(user)) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("id", user.path("id").asText());
        option.put("name", user.path("name").asText());
        option.put("userName", user.path("userName").asText());
        options.add(option);
      }
    }

    return jsonOk(options);
  }

  /** GET /bff/v1/users/{id} - 404 se o usuário existir globalmente mas não tiver nenhum grupo do
   *  Cardsync (mesmo comportamento de AdminUserService.get no NimbusFlowServer). */
  public ResponseEntity<byte[]> getByIdScopedToCardsync(
      Authentication auth, HttpServletRequest req, HttpServletResponse res, String id) {
    String token = accessTokenService.getValidAccessTokenOrRevoke(auth, req, res);
    JsonNode user = fetchUserById(token, id);

    if (user == null || !belongsToCardsync(user)) {
      return ResponseEntity.notFound().build();
    }

    return jsonOk(user);
  }

  /**
   * POST /bff/v1/users/search - injeta "advanced.groupAppKey=cardsync" no corpo antes de repassar,
   * pra o próprio NimbusAuth filtrar por app_key na specification (ver UserSpecs.groupAppKeyEquals)
   * e paginar/ordenar já só sobre os usuários do Cardsync. Antes disso a paginação vinha do
   * NimbusAuth sem esse recorte (base compartilhada por todos os apps Nimbus) e o filtro por app
   * era aplicado depois, na página já pronta - com poucos usuários do Cardsync frente ao total
   * global, a página filtrada podia vir vazia mesmo havendo usuários do Cardsync fora dela.
   */
  public ResponseEntity<byte[]> searchScopedToCardsync(
      Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    String token = accessTokenService.getValidAccessTokenOrRevoke(auth, req, res);
    byte[] requestBody = readBody(req);
    byte[] scopedBody = withGroupAppKeyFilter(requestBody);
    return sendJson(HttpMethod.POST, "/api/v1/users/search", token, scopedBody);
  }

  private byte[] withGroupAppKeyFilter(byte[] requestBody) {
    JsonNode requestJson = parse(requestBody);
    ObjectNode root = requestJson.isObject() ? (ObjectNode) requestJson : objectMapper.createObjectNode();

    JsonNode advancedNode = root.path("advanced");
    ObjectNode advanced = advancedNode.isObject() ? (ObjectNode) advancedNode : objectMapper.createObjectNode();
    advanced.put("groupAppKey", CARDSYNC_APP_KEY);
    root.set("advanced", advanced);

    try {
      return objectMapper.writeValueAsBytes(root);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao montar corpo de busca escopada ao Cardsync", e);
    }
  }

  private boolean belongsToCardsync(JsonNode user) {
    for (JsonNode g : user.path("groups")) {
      if (CARDSYNC_APP_KEY.equals(g.path("appKey").asText(null))) {
        return true;
      }
    }
    return false;
  }

  private List<JsonNode> searchAllUsersRaw(String token) {
    try {
      String responseBody = rest.post()
          .uri(nimbusAuthProps.getBaseUrl() + "/api/v1/users/search")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("page", 0, "size", 500))
          .retrieve()
          .body(String.class);

      JsonNode root = parse(responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8));
      List<JsonNode> items = new ArrayList<>();
      for (JsonNode candidate : root.path("_embedded").path("content")) {
        items.add(candidate);
      }
      return items;
    } catch (Exception e) {
      log.warn("Falha ao buscar usuários no NimbusAuth: {}", e.getMessage());
      return List.of();
    }
  }

  private ResponseEntity<byte[]> jsonOk(JsonNode body) {
    try {
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsBytes(body));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao montar corpo de resposta", e);
    }
  }

  private JsonNode fetchUserById(String token, String id) {
    try {
      String responseBody = rest.get()
          .uri(nimbusAuthProps.getBaseUrl() + "/api/v1/users/" + id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .retrieve()
          .body(String.class);
      return parse(responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      log.warn("Falha ao buscar usuário {} no NimbusAuth antes de editar: {}", id, e.getMessage());
      return null;
    }
  }

  private JsonNode findExistingUserByUserName(String token, String userName) {
    return searchAllUsersRaw(token).stream()
        .filter(u -> u.path("userName").asText("").equalsIgnoreCase(userName))
        .findFirst()
        .orElse(null);
  }

  private byte[] buildMergedUpdateBody(JsonNode existing, JsonNode requestJson) {
    Set<String> groupIds = new LinkedHashSet<>();
    for (JsonNode g : existing.path("groups")) {
      String id = g.path("id").asText(null);
      if (id != null) {
        groupIds.add(id);
      }
    }
    for (JsonNode g : requestJson.path("groupIds")) {
      groupIds.add(g.asText());
    }

    ObjectNode body = objectMapper.createObjectNode();
    body.put("userName", existing.path("userName").asText());
    body.put("name", existing.path("name").asText());
    body.put("document", existing.path("document").asText());
    ArrayNode groupIdsNode = body.putArray("groupIds");
    groupIds.forEach(groupIdsNode::add);

    try {
      return objectMapper.writeValueAsBytes(body);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao montar corpo de concessão de acesso a usuário existente", e);
    }
  }

  private ResponseEntity<byte[]> sendJson(HttpMethod method, String path, String token, byte[] body) {
    return rest.method(method)
        .uri(nimbusAuthProps.getBaseUrl() + path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange((request, response) -> {
          HttpStatusCode status = response.getStatusCode();
          byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());

          ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
          MediaType responseContentType = response.getHeaders().getContentType();
          if (responseContentType != null) {
            builder.contentType(responseContentType);
          }
          return builder.body(responseBody);
        });
  }

  private JsonNode parse(byte[] body) {
    try {
      if (body == null || body.length == 0) {
        return objectMapper.createObjectNode();
      }
      return objectMapper.readTree(body);
    } catch (Exception e) {
      throw new IllegalArgumentException("Corpo da requisição inválido", e);
    }
  }

  private byte[] readBody(HttpServletRequest req) {
    try {
      return StreamUtils.copyToByteArray(req.getInputStream());
    } catch (IOException e) {
      log.warn("Falha ao ler corpo da requisição: {}", e.getMessage());
      return new byte[0];
    }
  }
}
