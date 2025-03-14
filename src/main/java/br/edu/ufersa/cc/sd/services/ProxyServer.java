package br.edu.ufersa.cc.sd.services;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.dto.NotificationDto;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.Operation;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.exceptions.ConnectionException;
import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.exceptions.OperationException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.utils.Constants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProxyServer implements Runnable {

    private static final Timer TIMER = new Timer();
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class.getSimpleName());
    private static final List<Consumer<InetSocketAddress>> LISTENERS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final Long TIME_TO_CHANGE = 15_000L;

    @Getter
    private static InetSocketAddress address = new InetSocketAddress(Constants.getDefaultHost(), Constants.PROXY_PORT);
    private static InetSocketAddress localizationAddress = new InetSocketAddress(Constants.getDefaultHost(),
            Constants.LOCALIZATION_PORT);
    private static InetSocketAddress applicationAddress = new InetSocketAddress(Constants.getDefaultHost(),
            Constants.APPLICATION_PORT);
    private static ProxyServer instance = new ProxyServer();

    private final CacheService cacheService = new CacheService();

    @Getter
    private boolean isAlive = true;
    private ServerSocket serverSocket;
    private TimerTask changeAddressTask;

    public static ProxyServer getInstance() {
        if (instance == null) {
            instance = new ProxyServer();
        }

        return instance;
    }

    public static void addListenerWhenChangeAddress(final Consumer<InetSocketAddress> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListenerWhenChangeAddress(final Consumer<InetSocketAddress> listener) {
        LISTENERS.remove(listener);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(address.getPort());
            serverSocket.setReuseAddress(true);
            isAlive = true;

            LOG.info("Servidor Proxy iniciado");
            LOG.info("{}", serverSocket);

            new Thread(() -> waitForClients(serverSocket)).start();

            // Configurar mudança de porta a cada 30 segundos
            changeAddressTask = new TimerTask() {
                @Override
                public void run() {
                    changeAddress();
                }
            };
            TIMER.schedule(changeAddressTask, TIME_TO_CHANGE);
        } catch (final BindException e) {
            isAlive = false;
            LOG.error("Problema de conexão nessa porta");
        } catch (final IOException e) {
            isAlive = false;
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

        if (changeAddressTask != null) {
            changeAddressTask.cancel();
        }
        changeAddressTask = null;
    }

    private static void changeAddress() {
        LOG.info("Iniciando mudança de porta...");

        // Gerar uma nova porta aleatoriamente
        final var newPort = RANDOM.nextInt(10) + 8490;
        LOG.info("Nova porta escolhida: {}", newPort);

        // Tenta avisar ao servidor de localização que o endereço mudou
        if (notifyLocalizationService(newPort)) {
            LOG.info("Servidor de localização notificado");
            // Atualizar no atributo estático
            address = new InetSocketAddress(Constants.getDefaultHost(), newPort);

            // Reiniciar o servidor
            LOG.info("Reiniciando...");
            getInstance().stop();
            getInstance().run();

            if (getInstance().isAlive()) {
                LISTENERS.forEach(consumer -> consumer.accept(address));
                return;
            }
        }

        LOG.warn(
                "Não foi possível notificar o servidor de localização do novo endereço, então por enquanto permanece assim");

        // Agendar nova tentativa
        final var task = new TimerTask() {
            @Override
            public void run() {
                changeAddress();
            }
        };
        TIMER.schedule(task, TIME_TO_CHANGE);
    }

    private static boolean notifyLocalizationService(final Integer newPort) {
        try (final var socket = new Socket(localizationAddress.getHostString(), localizationAddress.getPort())) {
            final var output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            final var input = new ObjectInputStream(socket.getInputStream());

            final var notification = new NotificationDto(Nature.PROXY, address,
                    new InetSocketAddress(Constants.getDefaultHost(), newPort));
            final var request = new Request<>(Operation.LOCALIZE, notification);

            LOG.info("Enviando requisição...");
            output.writeObject(request);
            output.flush();

            LOG.info("Aguardando resposta...");
            @SuppressWarnings("unchecked")
            final var response = (Response<Serializable>) input.readObject();

            input.close();
            output.close();

            LOG.info("Conexão encerrada");

            return response.getStatus().equals(ResponseStatus.OK);
        } catch (final IOException e) {
            LOG.error("Servidor de localização não encontrado");
            return false;
        } catch (final ClassNotFoundException e) {
            LOG.error("A resposta não pôde ser interpretada", e);
            return false;
        }
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
            final var request = (Request<Order>) input.readObject();
            LOG.info("Executando operação {}...", request.getOperation());

            final Response<? extends Serializable> response;
            switch (request.getOperation()) {
                case LOCALIZE:
                    response = new Response<>(ResponseStatus.ERROR, "O servidor de Proxy não faz Localização");
                    break;

                case FIND:
                    response = getFromCache(request);
                    break;

                case UPDATE:
                    response = updateIncludingCache(request);
                    break;

                case DELETE:
                    response = deleteIncludingCache(request);
                    break;

                default:
                    response = redirectRequestToServer(request);
                    break;
            }

            output.writeObject(response);
            output.flush();

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
        try (final var socket = new Socket(applicationAddress.getHostString(), applicationAddress.getPort())) {
            LOG.info("Conectado ao servidor de aplicação");
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
