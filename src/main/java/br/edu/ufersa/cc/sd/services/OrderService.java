package br.edu.ufersa.cc.sd.services;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.repositories.OrderRepository;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class.getSimpleName());

    private final OrderRepository orderRepository = new OrderRepository();

    public static void initialize() throws SQLException {
        try (final var statement = OrderRepository.getConnection().createStatement()) {
            statement.executeUpdate(
                    "create table orders (code BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255), description VARCHAR(255), created_at TIMESTAMP, done_at TIMESTAMP)");
            LOG.info("Tabela criada");

            final var service = new OrderService();
            Stream.iterate(1, i -> i + 1)
                    .limit(100)
                    .forEach(i -> {
                        final var order = new Order()
                                .setName(i + "ª ordem")
                                .setDescription("Descrição da ordem nº " + i)
                                .setCreatedAt(LocalDateTime.now());

                        service.create(order);
                    });
        } catch (SQLException e) {
            throw new SQLException("Erro ao inicializar banco de dados", e);
        }
    }

    public Long countAll() {
        return orderRepository.countAll();
    }

    public List<Order> listAll() {
        LOG.info("Listando todas as ordens...");
        return orderRepository.listAll();
    }

    public void create(final Order order) {
        orderRepository.create(order);
        LOG.info("Ordem cadastrada");
    }

    public Order findByCode(final Long code) throws NotFoundException {
        LOG.info("Buscando ordem...");
        return orderRepository.findByCode(code);
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
