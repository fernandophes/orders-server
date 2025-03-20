package br.edu.ufersa.cc.sd.repositories;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.exceptions.OperationException;
import br.edu.ufersa.cc.sd.models.Order;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OrderRepository {

    private static final String URL = "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";
    private final String tableName;

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

    public List<Order> listAll() {
        try (final var statement = getConnection().createStatement()) {
            final var resultSet = statement
                    .executeQuery(
                            "select code, name, description, created_at, done_at from " + tableName
                                    + " order by created_at desc, code desc");

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

    public Order findByCode(final Long code) throws NotFoundException {
        final var sql = "select code, name, description, created_at, done_at from " + tableName + " where code = ?";
        try (final var statement = getConnection().prepareStatement(sql)) {
            statement.setLong(1, code);
            final var resultSet = statement.executeQuery();

            if (resultSet.first()) {
                final var createdAt = resultSet.getTimestamp("created_at");
                final var doneAt = resultSet.getTimestamp("done_at");

                return new Order()
                        .setCode(resultSet.getLong("code"))
                        .setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                        .setDescription(resultSet.getString("description"))
                        .setDoneAt(doneAt != null ? doneAt.toLocalDateTime() : null)
                        .setName(resultSet.getString("name"));
            } else {
                throw new NotFoundException();
            }
        } catch (final SQLException e) {
            throw new OperationException("Erro ao consultar ordem", e);
        }
    }

    public void create(final Order order) {
        final var sql = "insert into " + tableName + " (name, description, created_at) values (?, ?, ?)";

        try (final var statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, order.getName());
            statement.setString(2, order.getDescription());
            statement.setTimestamp(3, new Timestamp(
                    ZonedDateTime.of(order.getCreatedAt(), ZoneId.systemDefault()).toInstant().toEpochMilli()));

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Não foi possível cadastrar essa ordem");
            }
        } catch (final SQLException e) {
            throw new OperationException("Erro ao salvar ordem", e);
        }
    }

    public void update(final Order order) {
        final var sql = "update " + tableName + " set name = ?, description = ?, done_at = ? where code = ?";

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
                throw new SQLException("Não foi possível atualizar essa ordem");
            }
        } catch (final SQLException e) {
            throw new OperationException("Erro ao atualizar ordem", e);
        }
    }

    public void delete(final Order order) {
        final var sql = "delete from " + tableName + " where code = ?";

        try (final var statement = getConnection().prepareStatement(sql)) {
            statement.setLong(1, order.getCode());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Não foi possível excluir essa ordem");
            }
        } catch (final SQLException e) {
            throw new OperationException("Erro ao excluir ordem", e);
        }
    }

    public Long countAll() {
        try (final var statement = getConnection().createStatement()) {
            final var resultSet = statement.executeQuery("select count(*) from " + tableName + "");
            resultSet.next();
            return (long) resultSet.getInt(1);
        } catch (final SQLException e) {
            throw new OperationException("Erro ao contar ordens", e);
        }
    }

}
