package br.edu.ufersa.cc.sd.repositories;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.exceptions.OperationException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.services.OrderService;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OrderRepository {

    private static final Logger LOG = LoggerFactory.getLogger(OrderRepository.class.getSimpleName());

    private static final String URL = "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";
    private static final String TABLE = "orders";

    // Configurar acesso ao Banco de Dados
    private static Connection connection = null;

    // Abrir uma conexão única, e retornar a atual se já existir
    public static Connection getConnection() throws SQLException {
        if (connection == null) {
            try {
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
            } catch (final SQLException e) {
                throw new SQLException("Erro ao abrir conexão com Banco de Dados", e);
            }
        }
        return connection;
    }

    // Fechar a conexão, caso ela exista
    public static void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    public static void initialize() throws SQLException {
        try (final var statement = getConnection().createStatement()) {
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

    public List<Order> listAll() {
        try (final var statement = getConnection().createStatement()) {
            final var resultSet = statement
                    .executeQuery("select code, name, description, created_at, done_at from orders order by created_at desc, code desc");

            final var result = new ArrayList<Order>();
            while (resultSet.next()) {
                final var createdAt = resultSet.getTimestamp("created_at");
                final var doneAt = resultSet.getTimestamp("done_at");

                final var order = new Order()
                        .setCode(resultSet.getLong("code"))
                        .setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                        .setDescription(resultSet.getString("description"))
                        .setDoneAt(doneAt != null ? doneAt.toLocalDateTime() : null)
                        .setName(resultSet.getString("name"));

                result.add(order);
            }

            return result;
        } catch (final SQLException e) {
            throw new OperationException("Erro ao listar ordens", e);
        }
    }

    public void create(final Order order) {
        final var sql = "insert into " + TABLE + " (name, description, created_at) values (?, ?, ?)";

        try (final var statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, order.getName());
            statement.setString(2, order.getDescription());
            statement.setTimestamp(3, new Timestamp(
                    ZonedDateTime.of(order.getCreatedAt(), ZoneId.systemDefault()).toInstant().toEpochMilli()));

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Não foi possível cadastrar essa ordem.");
            }
        } catch (final SQLException e) {
            throw new OperationException("Erro ao salvar ordem", e);
        }
    }

    public void update(final Order order) {
        final var sql = "update " + TABLE + " set name = ?, description = ?, done_at = ? where code = ?";

        try (final var statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, order.getName());
            statement.setString(2, order.getDescription());

            if (order.getDoneAt() != null) {
                statement.setTimestamp(3, new Timestamp(
                        ZonedDateTime.of(order.getDoneAt(), ZoneId.systemDefault()).toInstant().toEpochMilli()));
            } else {
                statement.setTimestamp(3, null);
            }

            statement.setLong(4, order.getCode());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Não foi possível atualizar essa ordem.");
            }
        } catch (final SQLException e) {
            throw new OperationException("Erro ao atualizar ordem", e);
        }
    }

    public void delete(final Order order) {
        final var sql = "delete from " + TABLE + " where code = ?";

        try (final var statement = getConnection().prepareStatement(sql)) {
            statement.setLong(1, order.getCode());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Não foi possível excluir essa ordem.");
            }
        } catch (final SQLException e) {
            throw new OperationException("Erro ao excluir ordem", e);
        }
    }

    public Long countAll() {
        try (final var statement = getConnection().createStatement()) {
            final var resultSet = statement.executeQuery("select count(*) from orders");
            resultSet.next();
            return (long) resultSet.getInt(1);
        } catch (final SQLException e) {
            throw new OperationException("Erro ao contar ordens", e);
        }
    }

}
