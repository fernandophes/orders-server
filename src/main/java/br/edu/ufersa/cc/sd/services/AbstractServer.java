package br.edu.ufersa.cc.sd.services;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

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

    @Getter
    protected InetSocketAddress address = new InetSocketAddress(Constants.getDefaultHost(), Constants.APPLICATION_PORT);
    protected final Nature nature;

    @Getter
    protected boolean isAlive = true;
    protected ServerSocket serverSocket;

    protected AbstractServer(final Logger logger, final Nature nature) {
        this.logger = logger;
        this.nature = nature;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(address.getPort());
            serverSocket.setReuseAddress(true);
            isAlive = true;

            logger.info("Servidor {} iniciado", nature);
            logger.info("{}", serverSocket);

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
            final var input = new ObjectInputStream(client.getInputStream());

            // O servidor não pode receber chamadas externas
            if (client.getInetAddress().getHostAddress().equals("/127.0.0.1")) {
                logger.error("Cliente externo bloqueado: {}", client.getInetAddress());
                output.writeObject(new Response<>(ResponseStatus.ERROR, "Acesso não autorizado"));
                output.flush();
                client.close();
                return;
            }

            logger.info("Aguardando mensagens...");

            @SuppressWarnings("unchecked")
            final var request = (Request<Order>) input.readObject();
            logger.info("Executando operação {}...", request.getOperation());

            final Response<? extends Serializable> response;
            response = handleMessage(request);

            output.writeObject(response);
            output.flush();

            client.close();
            logger.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            logger.error("Erro ao aceitar novoc clientes", e);
        }
    }

    protected abstract Response<Serializable> handleMessage(final Request<? extends Serializable> request);

}
