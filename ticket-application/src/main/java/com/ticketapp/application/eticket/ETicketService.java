package com.ticketapp.application.eticket;

import com.ticketapp.domain.eticket.ETicket;
import com.ticketapp.domain.eticket.ETicketRepository;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderItem;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ETicketService {

    private static final String CODE_PREFIX = "TKT-";
    private static final int CODE_BYTES = 10;

    private final ETicketRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ETicketService(ETicketRepository repository) {
        this.repository = repository;
    }

    public List<ETicket> issueFor(Order order) {
        List<ETicket> existing = repository.findByOrderId(order.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        List<ETicket> issued = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                issued.add(repository.save(ETicket.issue(order.getId(), item.getTicketTypeId(), generateCode())));
            }
        }
        return issued;
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        return CODE_PREFIX + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
