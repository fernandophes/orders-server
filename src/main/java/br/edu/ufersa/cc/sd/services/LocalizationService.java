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
import lombok.Getter;

public class LocalizationService implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizationService.class.getSimpleName());

    @Getter
    private static InetSocketAddress address = new InetSocketAddress(Constants.getDefaultHost(),
            Constants.LOCALIZATION_PORT);
    private static InetSocketAddress proxyAddress = new InetSocketAddress(Constants.getDefaultHost(),
            Constants.PROXY_PORT);

    @Getter
    private boolean isAlive = true;
    private ServerSocket serverSocket;

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(address.getPort());
            serverSocket.setReuseAddress(true);
            isAlive = true;

            LOG.info("Servidor de localização iniciado");
            LOG.info("{}", serverSocket);

            LOG.info("Disponível pelo endereço {}:{}", proxyAddress.getHostString(), proxyAddress.getPort());
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

            final Response<InetSocketAddress> response;
            if (request.getOperation() == Operation.LOCALIZE) {
                if (client.getInetAddress().equals(proxyAddress.getAddress())) {
                    response = updateSocketAddress(request.getItem());
                } else {
                    response = new Response<>(proxyAddress);
                }
            } else {
                response = new Response<>(ResponseStatus.ERROR,
                        "O servidor de localização suporta apenas a operação " + Operation.LOCALIZE.toString(),
                        proxyAddress);
            }

            output.writeObject(response);
            output.flush();

            client.close();
            LOG.info("Cliente encerrado: {}", client.getInetAddress());
        } catch (final IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static Response<InetSocketAddress> updateSocketAddress(final Serializable item) {
        if (item instanceof InetSocketAddress) {
            proxyAddress = (InetSocketAddress) item;
            return new Response<>(ResponseStatus.OK);
        } else {
            return new Response<>(ResponseStatus.ERROR,
                    "O objeto precisa ser do tipo InetSocketAddress, tente novamente");
        }
    }

}
