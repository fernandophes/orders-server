package br.edu.ufersa.cc.sd.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.repositories.OrderRepository;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class.getSimpleName());

    private final OrderRepository orderRepository = new OrderRepository();

    public Long countAll() {
        return orderRepository.countAll();
    }

    public List<Order> listAll() {
        return orderRepository.listAll();
    }

    public void create(final Order order) {
        orderRepository.create(order);
        LOG.info("Ordem cadastrada");
    }

    public void update(final Order order) {
        orderRepository.update(order);
        LOG.info("Ordem atualizada");
    }

    public void delete(final Order order) {
        orderRepository.delete(order);
        LOG.info("Ordem excluída");
    }

}
