package com.climate.datas.utils.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RMICommon extends Remote {
  boolean isRunning() throws RemoteException;
  int numberOfClients() throws RemoteException;
  boolean insertPortRmi(List<Integer> rmiPorts) throws RemoteException;
  int incrementClients() throws RemoteException;
}