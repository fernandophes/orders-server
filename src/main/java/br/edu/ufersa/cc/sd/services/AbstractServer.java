package br.edu.ufersa.cc.sd.services;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;

import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.utils.Constants;
import lombok.Getter;

public abstract class AbstractServer implements Runnable {

    protected final Logger logger;

    protected final Set<InetSocketAddress> replicasAddresses = new HashSet<>();
    protected final Nature nature;

    @Getter
    protected InetSocketAddress address;

    @Getter
    protected boolean isAlive = true;
    protected ServerSocket serverSocket;

    protected AbstractServer(final Logger logger, final Nature nature, final Integer port) {
        this.logger = logger;
        this.nature = nature;
        this.address = new InetSocketAddress(Constants.getDefaultHost(), port);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(address.getPort());
            serverSocket.setReuseAddress(true);
            isAlive = true;

            logger.info("Servidor de {} iniciado", nature);
            logger.info("Disponível pelo endereço {}:{}", address.getHostString(), address.getPort());

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

        try {
            final var output = new ObjectOutputStream(client.getOutputStream());
            output.flush();
            final var input = new ObjectInputStream(client.getInputStream());

            // Validar cliente
            if (!validateClient(client)) {
                logger.error("Cliente não autorizado: {}", client.getInetAddress());
                output.writeObject(new Response<>(ResponseStatus.ERROR, "Acesso não autorizado"));
                output.flush();
                client.close();
                return;
            }

            logger.info("Aguardando mensagens...");

            @SuppressWarnings("unchecked")
            final var request = (Request<Order>) input.readObject();
            logger.info("Executando operação {}...", request.getOperation());

            output.writeObject(handleMessage(request));
            output.flush();

            client.close();
            logger.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            logger.error("Erro ao aceitar novos clientes", e);
        }
    }

    protected boolean validateClient(final Socket socket) {
        return socket != null;
    }

    protected abstract <T extends Serializable> Response<T> handleMessage(Request<? extends Serializable> request);

}
