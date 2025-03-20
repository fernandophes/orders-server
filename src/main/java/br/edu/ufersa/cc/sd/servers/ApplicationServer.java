package br.edu.ufersa.cc.sd.servers;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.contracts.RemoteApplication;
import br.edu.ufersa.cc.sd.contracts.RemoteProxy;
import br.edu.ufersa.cc.sd.dto.Combo;
import br.edu.ufersa.cc.sd.dto.Notification;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.enums.Nature;
import br.edu.ufersa.cc.sd.enums.ResponseStatus;
import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.models.Order;
import br.edu.ufersa.cc.sd.services.OrderService;

public class ApplicationServer extends AbstractServer implements RemoteApplication {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationServer.class.getSimpleName());

    private final Set<Combo> proxies = new HashSet<>();
    private final InetSocketAddress primary;
    private final Set<Combo> backups = new HashSet<>();
    private final OrderService orderService = new OrderService();

    public ApplicationServer(final InetSocketAddress primary) {
        super(LOG, Nature.APPLICATION);

        this.primary = primary;
        if (primary != null) {
            try {
                final var registry = LocateRegistry.getRegistry(primary.getHostString(), primary.getPort());
                final var primaryStub = (RemoteApplication) registry.lookup(Nature.APPLICATION.getName());

                primaryStub.addBackup(new Combo(serverSocketAddress, remoteAddress));
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            getRemoteAddress(this);
        } catch (IOException | AlreadyBoundException e) {
            LOG.error("Não foi possível abrir um endereço remoto", e);
            return;
        }

        super.run();

        try {
            LOG.info("Inicializando banco de dados...");
            orderService.initialize(serverSocketAddress);
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        super.close();

        if (primary != null) {
            try {
                final var registry = LocateRegistry.getRegistry(primary.getHostString(), primary.getPort());
                final var primaryStub = (RemoteApplication) registry.lookup(Nature.APPLICATION.getName());

                primaryStub.removeBackup(new Combo(serverSocketAddress, remoteAddress));
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }

        for (final var proxy : proxies) {
            try {
                final var proxyAddr = proxy.getRemoteAddress();
                final var registry = LocateRegistry.getRegistry(proxyAddr.getHostString(), proxyAddr.getPort());
                final var proxyStub = (RemoteProxy) registry.lookup(Nature.PROXY.getName());

                proxyStub.setApplicationAddress(serverSocketAddress);
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> Response<T> handleMessage(final Request<? extends Serializable> request) {

        switch (request.getOperation()) {
            case ATTACH:
                addProxy((Notification) request.getItem());
                return Response.ok();

            case DETACH:
                removeProxy((Notification) request.getItem());
                return Response.ok();

            case LOCALIZE:
                return new Response<>(ResponseStatus.ERROR, "O servidor de Aplicação não faz Localização");

            case LIST:
                final var list = orderService.listAll();
                return new Response<>((T) new ArrayList<>(list));

            case CREATE:
                ((Order) request.getItem()).setCode(null);
                orderService.create((Order) request.getItem());
                propagateToBackup(request);
                return Response.ok();

            case FIND:
                try {
                    return new Response<>((T) orderService.findByCode(((Order) request.getItem()).getCode()));
                } catch (final NotFoundException e) {
                    return new Response<>(ResponseStatus.ERROR, "Ordem não encontrada");
                }

            case UPDATE:
                orderService.update((Order) request.getItem());
                propagateToBackup(request);
                return Response.ok();

            case DELETE:
                orderService.delete((Order) request.getItem());
                propagateToBackup(request);
                return Response.ok();

            case COUNT:
                return new Response<>((T) orderService.countAll());

            default:
                return new Response<>(ResponseStatus.ERROR, "Operação não reconhecida");
        }
    }

    private void propagateToBackup(final Request<? extends Serializable> request) {
        // Propagar para o backup
        for (final var backup : backups) {
            try {
                final var registry = LocateRegistry.getRegistry(backup.getRemoteAddress().getHostName(),
                        backup.getRemoteAddress().getPort());
                final var stub = (RemoteApplication) registry.lookup(Nature.APPLICATION.getName());

                stub.handleMessage(request);
            } catch (final RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void addProxy(Notification proxy) {
        proxies.add(new Combo(proxy.getServerSocketAddress(), proxy.getRemoteAddress()));
    }

    private void removeProxy(Notification proxy) {
        proxies.remove(new Combo(proxy.getServerSocketAddress(), proxy.getRemoteAddress()));
    }

    @Override
    public void addProxy(Combo proxy) throws RemoteException {
        proxies.add(proxy);
    }

    @Override
    public void removeProxy(Combo proxy) throws RemoteException {
        proxies.remove(proxy);
    }

    @Override
    public void addBackup(Combo backup) throws RemoteException {
        proxies.add(backup);
    }

    @Override
    public void removeBackup(Combo backup) throws RemoteException {
        proxies.remove(backup);
    }

}
