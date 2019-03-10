/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package dxi.server;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

// import org.web3j.generated.*;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.StaticGasProvider;


public class App {
    private static final Web3j web3 = Web3j.build(new HttpService());  // defaults to http://localhost:8545/
    private static final StaticGasProvider gasProvider = new StaticGasProvider(BigInteger.ZERO, DefaultGasProvider.GAS_LIMIT);
    
    public List<String> getAccounts() throws IOException {
        return web3.ethAccounts().send().getAccounts();
    }

    public static void main(String[] args) throws Exception {
        
    

        var accounts = web3.ethAccounts().send().getAccounts();
        var ctm1 = new ClientTransactionManager(web3, accounts.get(0));
        var ctm2 = new ClientTransactionManager(web3, accounts.get(1));


        String dutchExchangeAddress = "0x13274fe19c0178208bcbee397af8167a7be27f6f";
        String dxInteractsAddress = "0x2a504b5e7ec284aca5b6f49716611237239f0b97";
        String wethAddress = "0x345ca3e014aaf5dca488057592ee47305d9b3e10";

        DutchExchange dx = new DutchExchange(dutchExchangeAddress, web3, ctm1, gasProvider);
        DxInteracts dxi = new DxInteracts(dxInteractsAddress, web3, ctm1, gasProvider);

        var balance = dx.balances(wethAddress, accounts.get(0)).send();
        System.out.println("weth balance in dx of acc[0]: " + balance.toString());


    }
}
