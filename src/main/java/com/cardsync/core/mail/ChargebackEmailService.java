package com.cardsync.core.mail;

import com.cardsync.core.conciliation.analysis.ConciliationDebitChargebackClassifier;
import com.cardsync.core.config.EmailSettingsService;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.InstallmentUnschedulingEntity;
import com.cardsync.domain.model.PendingDebtEntity;
import com.cardsync.domain.model.SettledDebtEntity;
import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargebackEmailService {

  private static final String INLINE_LOGO_CID = "cardsync-logo";
  private static final String INLINE_LOGO_PATH = "static/assets/cardsync-logo.png";
  private static final String INLINE_LOGO_CONTENT_TYPE = "image/png";

  private static final Locale PT_BR = Locale.of("pt", "BR");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final EmailSenderService emailSender;
  private final EmailSettingsService emailSettingsService;
  private final ConciliationDebitChargebackClassifier chargebackClassifier;

  public void notifyChargebacksFound(
    String fileName,
    List<PendingDebtEntity> pendingDebts,
    List<AdjustmentEntity> adjustments,
    List<SettledDebtEntity> settledDebts,
    List<InstallmentUnschedulingEntity> unschedulings
  ) {
    try {
      List<String> recipients = emailSettingsService.getChargebackRecipients();
      if (recipients.isEmpty()) return;

      List<ChargebackInfo> chargebacks = new ArrayList<>();

      for (PendingDebtEntity d : pendingDebts) {
        if (chargebackClassifier.isChargeback(d)) {
          chargebacks.add(new ChargebackInfo(
            "Débito Pendente",
            pv(d.getPvNumber()),
            money(d.getValueDebitOrder() != null ? d.getValueDebitOrder() : d.getPendingValue()),
            date(d.getDateDebitOrder() != null ? d.getDateDebitOrder() : d.getDateOriginalTransaction()),
            str(d.getReasonDescription())
          ));
        }
      }

      for (AdjustmentEntity a : adjustments) {
        if (chargebackClassifier.isChargeback(a)) {
          Integer pvNum = a.getPvNumberOriginal() != null ? a.getPvNumberOriginal() : a.getPvNumber();
          LocalDate dt = a.getAdjustmentDate() != null ? a.getAdjustmentDate() : a.getTransactionDate();
          chargebacks.add(new ChargebackInfo(
            "Ajuste",
            pv(pvNum),
            money(a.getAdjustmentValue() != null ? a.getAdjustmentValue() : a.getTransactionValue()),
            date(dt),
            str(a.getAdjustmentDescription())
          ));
        }
      }

      for (SettledDebtEntity s : settledDebts) {
        if (chargebackClassifier.isChargeback(s)) {
          chargebacks.add(new ChargebackInfo(
            "Débito Liquidado",
            pv(s.getPvNumber()),
            money(s.getLiquidatedValue() != null ? s.getLiquidatedValue() : s.getValueDebitOrder()),
            date(s.getLiquidatedDate() != null ? s.getLiquidatedDate() : s.getDateDebitOrder()),
            str(s.getReasonDescription())
          ));
        }
      }

      for (InstallmentUnschedulingEntity u : unschedulings) {
        if (chargebackClassifier.isChargeback(u)) {
          Integer pvNum = u.getPvNumberOriginal() != null ? u.getPvNumberOriginal() : u.getAdjustedPvNumber();
          LocalDate dt = u.getCancellationDate() != null ? u.getCancellationDate() : u.getTransactionDate();
          chargebacks.add(new ChargebackInfo(
            "Desagendamento",
            pv(pvNum),
            money(u.getCancellationValue() != null ? u.getCancellationValue() : u.getAdjustmentValue()),
            date(dt),
            str(u.getTypeDebit())
          ));
        }
      }

      if (chargebacks.isEmpty()) return;

      log.info("🔔 {} chargeback(s) detectado(s) em {}. Notificando {} destinatário(s).",
        chargebacks.size(), fileName, recipients.size());

      var builder = EmailSenderService.Message.builder()
        .subject("CardSync – " + chargebacks.size() + " chargeback(s) detectado(s) em " + fileName)
        .template("mail/chargeback-mail.html")
        .eventType(EmailLogEventTypeEnum.CHARGEBACK_DETECTED)
        .data("chargebacks", chargebacks)
        .data("fileName", fileName)
        .data("totalCount", chargebacks.size())
        .data("logoCid", "cid:" + INLINE_LOGO_CID)
        .inline(EmailSenderService.InlineResource.builder()
          .contentId(INLINE_LOGO_CID)
          .resource(new ClassPathResource(INLINE_LOGO_PATH))
          .contentType(INLINE_LOGO_CONTENT_TYPE)
          .build());

      for (String recipient : recipients) {
        try {
          emailSender.sendThymeleaf(builder.to(recipient).build());
        } catch (Exception ex) {
          log.error("❌ Falha ao notificar {} sobre chargeback(s) em {}: {}", recipient, fileName, ex.getMessage());
        }
      }

    } catch (Exception ex) {
      log.error("❌ Erro ao processar notificação de chargeback para {}: {}", fileName, ex.getMessage(), ex);
    }
  }

  private static String pv(Integer pv) {
    return pv != null ? pv.toString() : "-";
  }

  private static String money(BigDecimal value) {
    if (value == null) return "-";
    return NumberFormat.getCurrencyInstance(PT_BR).format(value);
  }

  private static String date(LocalDate d) {
    return d != null ? d.format(DATE_FMT) : "-";
  }

  private static String str(String s) {
    return s != null && !s.isBlank() ? s.trim() : "-";
  }

  public record ChargebackInfo(
    String tipo,
    String pv,
    String valor,
    String data,
    String descricao
  ) {}
}
