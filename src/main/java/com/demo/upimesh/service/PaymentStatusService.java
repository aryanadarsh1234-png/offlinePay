package com.demo.upimesh.service;

import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentStatusService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    public PaymentStatusService(TransactionRepository transactionRepository,
                                IdempotencyService idempotencyService) {
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
    }

    public PaymentStatusResponse getStatus(String packetHash) {
        if (idempotencyService.isDuplicate(packetHash)) {
            Optional<Transaction> tx = transactionRepository.findByPacketHash(packetHash);
            if (tx.isPresent()) {
                return new PaymentStatusResponse("SETTLED", tx.get());
            } else {
                return new PaymentStatusResponse("DUPLICATE_DROPPED", null);
            }
        }
        return new PaymentStatusResponse("PENDING", null);
    }

    public static class PaymentStatusResponse {
        public String status;
        public String from;
        public String to;
        public Double amount;
        public String settledAt;

        public PaymentStatusResponse(String status, Transaction tx) {
            this.status = status;
            if (tx != null) {
                this.from = tx.getSenderVpa();
                this.to = tx.getReceiverVpa();
                this.amount = tx.getAmount().doubleValue();
                this.settledAt = tx.getSettledAt() != null ? tx.getSettledAt().toString() : null;
            }
        }
    }
}