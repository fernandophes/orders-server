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
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.utils.JsonUtils;

public class SocketService implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(SocketService.class.getSimpleName());

    private OrderService orderService = new OrderService();
    private ServerSocket serverSocket;
    private boolean isAlive = true;

    @Override
    public void run() {
        LOG.info("Servidor iniciado");

        try {
            serverSocket = new ServerSocket(8484);
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
            LOG.info("Servidor encerrado.");
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(final Socket client) {
        LOG.info("Cliente conectado: {}", client.getInetAddress());
        try {
            final var output = new ObjectOutputStream(client.getOutputStream());
            final var input = new ObjectInputStream(client.getInputStream());

            LOG.info("Aguardando mensagens...");

            @SuppressWarnings("unchecked")
            final var request = (Request<Order>) input.readObject();
            final var order = request.getItem();
            LOG.info("Executando operação {}...", request.getOperation());

            final Response<Serializable> response;
            switch (request.getOperation()) {
                case LIST:
                    final var list = orderService.listAll();
                    response = new Response<>(new ArrayList<>(list));
                    break;

                case CREATE:
                    order.setCode(null);
                    LOG.info(JsonUtils.toJson(order));
                    orderService.create(order);
                    response = new Response<>(ResponseStatus.OK);
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
                default:
                    response = new Response<>(orderService.countAll());
                    break;
            }

            output.writeObject(response);

            client.close();
            LOG.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

}
