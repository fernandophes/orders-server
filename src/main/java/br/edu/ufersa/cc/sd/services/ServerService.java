package br.edu.ufersa.cc.sd.services;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.utils.Constants;
import lombok.Getter;

public class ServerService implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ServerService.class.getSimpleName());

    @Getter
    private boolean isAlive = true;
    private ServerSocket serverSocket;
    private OrderService orderService = new OrderService();

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(Constants.APPLICATION_PORT);
            serverSocket.setReuseAddress(true);
            isAlive = true;

            LOG.info("Servidor iniciado");
            LOG.info("{}", serverSocket);

            new Thread(() -> waitForClients(serverSocket)).start();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

        isAlive = false;
        serverSocket = null;
    }

    private void waitForClients(final ServerSocket serverSocket) {
        try {
            while (isAlive) {
                LOG.info("Aguardando clientes...");
                final var client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (final SocketException e) {
            LOG.info("Servidor encerrado");
        } catch (final IOException e) {
            LOG.error("Erro ao aceitar novos clientes", e);
        }
    }

    private void handleClient(final Socket client) {
        LOG.info("Cliente conectado: {}", client.getInetAddress());

        try {
            final var output = new ObjectOutputStream(client.getOutputStream());
            final var input = new ObjectInputStream(client.getInputStream());

            // O servidor não pode receber chamadas externas
            if (client.getInetAddress().getHostAddress().equals("/127.0.0.1")) {
                LOG.error("Cliente externo bloqueado: {}", client.getInetAddress());
                output.writeObject(new Response<>(ResponseStatus.ERROR, "Acesso não autorizado"));
                output.flush();
                client.close();
                return;
            }

            LOG.info("Aguardando mensagens...");

            @SuppressWarnings("unchecked")
            final var request = (Request<Order>) input.readObject();
            final var order = request.getItem();
            LOG.info("Executando operação {}...", request.getOperation());

            final Response<? extends Serializable> response;
            switch (request.getOperation()) {
                case LOCALIZE:
                    response = new Response<>(ResponseStatus.ERROR, "O servidor de Dados não faz Localização");
                    break;

                case LIST:
                    final var list = orderService.listAll();
                    response = new Response<>(new ArrayList<>(list));
                    break;

                case CREATE:
                    order.setCode(null);
                    orderService.create(order);
                    response = new Response<>(ResponseStatus.OK);
                    break;

                case FIND:
                    response = tryToFind(order.getCode());
                    break;

                case UPDATE:
                    orderService.update(order);
                    response = new Response<>(ResponseStatus.OK);
                    break;

                case DELETE:
                    orderService.delete(order);
                    response = new Response<>(ResponseStatus.OK);
                    break;

                case COUNT:
                    response = new Response<>(orderService.countAll());
                    break;

                default:
                    response = new Response<>(ResponseStatus.ERROR, "Operação não reconhecida");
            }

            output.writeObject(response);
            output.flush();

            client.close();
            LOG.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            LOG.error("Erro ao aceitar novoc clientes", e);
        }
    }

    private Response<Order> tryToFind(final Long code) {
        try {
            return new Response<>(orderService.findByCode(code));
        } catch (NotFoundException e) {
            return new Response<>(ResponseStatus.ERROR, "Ordem não encontrada");
        }
    }

}
