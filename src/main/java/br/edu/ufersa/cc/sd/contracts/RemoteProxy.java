package br.edu.ufersa.cc.sd.contracts;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

import br.edu.ufersa.cc.sd.dto.Combo;
import br.edu.ufersa.cc.sd.dto.Request;
import br.edu.ufersa.cc.sd.dto.Response;
import br.edu.ufersa.cc.sd.models.Order;

public interface RemoteProxy extends Remote {

    public Order get(final Long code) throws RemoteException;

    public Combo getLeader() throws RemoteException;

    public RemoteProxy setLeader(final Combo proxy) throws RemoteException;

    public boolean isLeader() throws RemoteException;

    public Response<Order> updateIncludingCache(final Request<Order> request) throws RemoteException;

    public void updateInCache(final Order order) throws RemoteException;

    public Response<Order> deleteIncludingCache(final Request<Order> request) throws RemoteException;

    public void deleteInCache(final Order order) throws RemoteException;

    public RemoteProxy setApplicationAddress(final InetSocketAddress address) throws RemoteException;

}
