package com.cardsync.core.config;

import com.cardsync.domain.service.EmailLogService;
import com.cardsync.domain.service.EmailSenderService;
import com.cardsync.infrastructure.mail.BrevoEmailSenderService;
import com.cardsync.infrastructure.mail.EmailTemplateProcessor;
import com.cardsync.infrastructure.mail.FakeEmailSenderService;
import com.cardsync.infrastructure.mail.SmtpEmailSenderService;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Único bean Spring do tipo EmailSenderService - resolve a implementação de novo (banco, via
 * EmailSettingsService.getImpl(), cacheado e invalidado no update()) A CADA envio, em vez de
 * escolher uma implementação fixa no boot como antes (EmailConfig, removido). Isso é o que faz a
 * troca SMTP/API_KEY/FAKE na tela "Configurações &gt; E-mail" valer imediatamente, sem precisar
 * reiniciar o processo - antes, salvar um impl novo na tela não tinha efeito nenhum até o próximo
 * deploy/restart, porque o @Bean só era construído uma vez. Mesmo ajuste feito no NimbusFlowServer
 * (com.nimbusflow.common.notification.mail.EmailSenderServiceRouter).
 *
 * <p>Reconstruir Fake/Smtp/Brevo a cada chamada é barato (nenhum guarda estado/conexão pooled -
 * SmtpEmailSenderService já reconstruía o JavaMailSender a cada envio) e evita qualquer lógica
 * extra de invalidação além da que EmailSettingsService já tem; em especial, a baseUrl do
 * RestClient do Brevo também precisa ser lida de novo a cada chamada (não só o impl), senão uma
 * troca de brevoBaseUrl na tela sofreria o mesmo problema de ficar "presa" até reiniciar.
 */
@Service
@RequiredArgsConstructor
public class EmailSenderServiceRouter implements EmailSenderService {

  private final EmailSettingsService emailSettingsService;
  private final EmailLogService emailLogService;
  private final RestClient.Builder restClientBuilder;
  private final EmailTemplateProcessor emailTemplateProcessor;

  @Override
  public void sendFreemarker(Message message) {
    delegate().sendFreemarker(message);
  }

  @Override
  public void sendThymeleaf(Message message) {
    delegate().sendThymeleaf(message);
  }

  private EmailSenderService delegate() {
    return switch (emailSettingsService.getImpl()) {
      case FAKE -> new FakeEmailSenderService(emailTemplateProcessor);
      case SMTP -> new SmtpEmailSenderService(emailSettingsService, emailTemplateProcessor, emailLogService);
      case API_KEY -> new BrevoEmailSenderService(
          restClientBuilder
              .baseUrl(emailSettingsService.getBrevoBaseUrl())
              .requestFactory(httpTimeouts())
              .build(),
          emailSettingsService,
          emailLogService,
          emailTemplateProcessor);
    };
  }

  /** Sem isto, um host/rede bloqueada (proxy/firewall do provedor de hospedagem) trava a chamada
   *  HTTP até o timeout default do cliente - sendThymeleaf()/sendFreemarker() são sempre chamados
   *  de dentro da mesma transação/requisição HTTP que salva a entidade de negócio, então travar
   *  aqui trava a requisição inteira (mesmo racional do timeout em
   *  SmtpEmailSenderService#buildMailSender). JdkClientHttpRequestFactory (core do Spring
   *  Framework) em vez de ClientHttpRequestFactorySettings/ClientHttpRequestFactories (Spring
   *  Boot) - essas classes de conveniência do Boot mudaram de pacote entre a versão usada aqui
   *  (4.0.2) e a do NimbusFlowServer (3.3.2), então usar a API do framework direto evita a
   *  divergência entre os dois. */
  private static ClientHttpRequestFactory httpTimeouts() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(10));
    return factory;
  }
}
