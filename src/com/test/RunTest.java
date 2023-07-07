package com.test;

import java.io.IOException;

import org.voltdb.autojar.AutoJar;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
public class RunTest {
  


  public static void main(String[] args) throws Exception {
    // TODO Auto-generated method stub
    
    try {
      Client c = connectVoltDB("localhost");
      AutoJar aj = AutoJar.getInstance();
      aj.load("com.test",c,null);
    } catch (ClassNotFoundException | IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }
  private static Client connectVoltDB(String hostname) throws Exception {
    Client client = null;
    ClientConfig config = null;

    try {
      AutoJar.msg("Logging into VoltDB");

        config = new ClientConfig(); // "admin", "idontknow");
        //config.setMaxOutstandingTxns(20000);
        //config.setMaxTransactionsPerSecond(200000);
        config.setTopologyChangeAware(false);
        config.setReconnectOnConnectionLoss(true);
        //config.setConnectionResponseTimeout(1000);

        client = ClientFactory.createClient(config);

        String[] hostnameArray = hostname.split(",");

        for (int i = 0; i < hostnameArray.length; i++) {
          AutoJar.msg("Connect to " + hostnameArray[i] + "...");
            try {
                client.createConnection(hostnameArray[i]);
            } catch (Exception e) {
              AutoJar.msg(e.getMessage());
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
        throw new Exception("VoltDB connection failed.." + e.getMessage(), e);
    }

    return client;

}

}
