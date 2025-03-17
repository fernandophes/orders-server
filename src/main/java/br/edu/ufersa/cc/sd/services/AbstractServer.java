package br.edu.ufersa.cc.sd.services;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;

import br.edu.ufersa.cc.sd.dto.Notification;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.Operation;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.utils.Constants;
import lombok.Getter;

public abstract class AbstractServer implements Runnable, Closeable {

    protected static final Random RANDOM = new Random();

    protected final Logger logger;

    @Getter
    protected final Nature nature;
    protected final Set<InetSocketAddress> replicasAddresses = new HashSet<>();

    @Getter
    protected InetSocketAddress address;

    @Getter
    protected boolean isAlive = true;
    protected ServerSocket serverSocket;

    protected Integer triesToBind = 0;

    protected AbstractServer(final Logger logger, final Nature nature) {
        this.logger = logger;
        this.nature = nature;
    }

    @Override
    public void run() {
        try {
            final var port = RANDOM.nextInt(Constants.RANGE_SIZE) + nature.getPortRange();

            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            address = new InetSocketAddress(Constants.getDefaultHost(), port);
            isAlive = true;

            logger.info("Servidor de {} iniciado em {}:{}", nature, address.getHostString(), port);

            new Thread(() -> waitForClients(serverSocket)).start();
        } catch (final BindException e) {
            logger.warn("Tentei nascer em uma porta ocupada, tentando em outra...");
            triesToBind++;

            if (triesToBind < Constants.RANGE_SIZE) {
                run();
            } else {
                e.printStackTrace();
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
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

    protected void waitForClients(final ServerSocket serverSocket) {
        try {
            while (isAlive) {
                logger.info("Aguardando clientes...");
                final var client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (final SocketException e) {
            logger.info("Servidor encerrado");
        } catch (final IOException e) {
            logger.error("Erro ao aceitar novos clientes", e);
        }
    }

    protected void handleClient(final Socket client) {
        logger.info("Cliente conectado: {}", client.getInetAddress());

        try (client;
                final var output = new ObjectOutputStream(client.getOutputStream());
                final var input = new ObjectInputStream(client.getInputStream());) {
            output.flush();

            // Validar cliente
            if (!validateClient(client)) {
                logger.error("Cliente não autorizado: {}", client.getInetAddress());
                output.writeObject(new Response<>(ResponseStatus.ERROR, "Acesso não autorizado"));
                output.flush();
                return;
            }

            logger.info("Aguardando mensagens...");

            @SuppressWarnings("unchecked")
            final var request = (Request<Order>) input.readObject();
            logger.info("Executando operação {}...", request.getOperation());

            output.writeObject(handleMessage(request));
            output.flush();

            logger.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            logger.error("Erro ao aceitar novos clientes", e);
        }
    }

    protected boolean validateClient(final Socket socket) {
        return socket != null;
    }

    protected abstract <T extends Serializable> Response<T> handleMessage(Request<? extends Serializable> request);

    protected boolean attachTo(final InetSocketAddress targetAddress) {
        final var notification = new Notification(Nature.PROXY, address);
        final var request = new Request<>(Operation.ATTACH, notification);
        final var response = send(targetAddress, request);

        return response.getStatus() == ResponseStatus.OK;
    }

    protected boolean detachFrom(final InetSocketAddress targetAddress) {
        final var notification = new Notification(Nature.PROXY, address);
        final var request = new Request<>(Operation.DETACH, notification);
        final var response = send(targetAddress, request);

        return response.getStatus() == ResponseStatus.OK;
    }

    protected <T extends Serializable> Response<T> send(final InetSocketAddress targetAddress,
            final Request<? extends Serializable> request) {
        try (final var socket = new Socket(targetAddress.getHostString(), targetAddress.getPort());
                final var output = new ObjectOutputStream(socket.getOutputStream());
                final var input = new ObjectInputStream(socket.getInputStream());) {
            output.flush();

            logger.info("Enviando requisição...");
            output.writeObject(request);
            output.flush();

            logger.info("Aguardando resposta...");
            @SuppressWarnings("unchecked")
            final var response = (Response<T>) input.readObject();

            logger.info("Conexão encerrada");
            return response;
        } catch (final IOException e) {
            logger.error("Servidor de localização não encontrado");
            return Response.error();
        } catch (final ClassNotFoundException e) {
            logger.error("A resposta não pôde ser interpretada", e);
            return Response.error();
        }
    }

}
