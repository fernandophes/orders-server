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
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Operation;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.utils.Constants;

public class LocalizationService implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizationService.class.getSimpleName());

    private ServerSocket serverSocket;
    private boolean isAlive = true;

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(Constants.LOCALIZATION_PORT);
            LOG.info("Servidor de localização iniciado");
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
            final var request = (Request<Serializable>) input.readObject();
            LOG.info("Executando operação {}...", request.getOperation());

            final var address = new InetSocketAddress("localhost", Constants.PROXY_PORT);
            
            final Response<InetSocketAddress> response;
            if (request.getOperation() == Operation.LOCALIZE) {
                response = new Response<>(address);
            } else {
                response = new Response<>(ResponseStatus.ERROR,
                        "O servidor de localização suporta apenas a operação " + Operation.LOCALIZE.toString(), address);
            }

            output.writeObject(response);

            client.close();
            LOG.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

}
