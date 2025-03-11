package br.edu.ufersa.cc.sd.services;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.exceptions.ConnectionException;
import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.exceptions.OperationException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.utils.Constants;

public class ProxyService implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyService.class.getSimpleName());

    private final CacheService cacheService = new CacheService();
    private ServerSocket serverSocket;
    private boolean isAlive = true;

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(Constants.PROXY_PORT);
            LOG.info("Servidor Proxy iniciado");
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
            LOG.info("Executando operação {}...", request.getOperation());

            final Response<? extends Serializable> response;
            switch (request.getOperation()) {
                case FIND:
                    response = getFromCache(request);
                    break;

                case UPDATE:
                    response = updateIncludingCache(request);
                    break;

                case DELETE:
                    response = deleteIncludingCache(request);
                    break;

                case LIST:
                case CREATE:
                case COUNT:
                default:
                    response = redirectRequestToServer(request);
                    break;
            }

            output.writeObject(response);

            client.close();
            LOG.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Response<Order> getFromCache(final Request<Order> request) {
        final var result = cacheService.find(request.getItem().getCode(),
                () -> {
                    final Response<Order> resp = redirectRequestToServer(request);
                    if (resp.getStatus() == ResponseStatus.ERROR) {
                        throw new NotFoundException();
                    }
                    return resp.getItem();
                });

        if (result != null) {
            return new Response<>(result, "Ordem encontrada");
        } else {
            return new Response<>(ResponseStatus.ERROR, "A ordem não existe na base de dados");
        }
    }

    private Response<Order> updateIncludingCache(final Request<Order> request) {
        // Editar na base de dados
        final Response<Order> response = redirectRequestToServer(request);

        // Atualizar no cache
        cacheService.update(request.getItem());

        return response;
    }

    private Response<Order> deleteIncludingCache(final Request<Order> request) {
        // Remover na base de dados
        final Response<Order> response = redirectRequestToServer(request);

        // Remover no cache
        cacheService.delete(request.getItem());

        return response;
    }

    private <O extends Serializable> Response<O> redirectRequestToServer(final Request<Order> request) {
        try (final var socket = new Socket("localhost", Constants.SERVER_PORT)) {
            LOG.info("Conectado ao servidor");
            final var output = new ObjectOutputStream(socket.getOutputStream());
            final var input = new ObjectInputStream(socket.getInputStream());

            LOG.info("Enviando requisição...");
            output.writeObject(request);
            output.flush();

            LOG.info("Aguardando resposta...");
            @SuppressWarnings("unchecked")
            final var response = (Response<O>) input.readObject();

            input.close();
            output.close();

            LOG.info("Conexão encerrada");
            return response;
        } catch (final IOException e) {
            throw new ConnectionException(e);
        } catch (final ClassNotFoundException e) {
            throw new OperationException("A resposta não pôde ser interpretada", e);
        }
    }

}
