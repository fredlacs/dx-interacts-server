/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package dxi.server;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.StaticGasProvider;


public class App {
    private static final Web3j web3 = Web3j.build(new HttpService());  // defaults to http://localhost:8545/
    private static final StaticGasProvider gasProvider = new StaticGasProvider(BigInteger.ZERO, DefaultGasProvider.GAS_LIMIT);
    
    public static List<String> getAccounts() throws IOException {
        return web3.ethAccounts().send().getAccounts();
    }

    private static BigInteger toWei(Long ethValue) {
        BigInteger weiValue = BigInteger.valueOf(ethValue).multiply(BigInteger.valueOf(10).pow(18));
        return weiValue;
    }

    private static void evmSkipTime(Integer seconds) throws IOException {
        new Request<>("evm_increaseTime", Arrays.asList(seconds), new HttpService(), Response.class).send();
        new Request<>("evm_mine", Collections.EMPTY_LIST, new HttpService(), Response.class).send();
    }

    public static void main(String[] args) throws Exception {
        var accounts = getAccounts();
        var ctm1 = new ClientTransactionManager(web3, accounts.get(0));
        // var ctm2 = new ClientTransactionManager(web3, accounts.get(1));

        String dutchExchangeAddress = "0x2a504B5e7eC284ACa5b6f49716611237239F0b97";
        String dxInteractsAddress = "0x4e71920b7330515faf5EA0c690f1aD06a85fB60c";
        String wethAddress = "0xf204a4Ef082f5c04bB89F7D5E6568B796096735a";
        String gnoAddress = "0x2C2B9C9a4a25e24B174f26114e8926a9f2128FE4";

        HashMap<String, String> contractName = new HashMap<>() {{
            put(dutchExchangeAddress.toUpperCase(), "DutchExchange");
            put(dxInteractsAddress.toUpperCase(), "DxInteracts");
            put(wethAddress.toUpperCase(), "WETH");
            put(gnoAddress.toUpperCase(), "GNO");
        }};

        // to call functions with different accounts, choose a different ClientTransactionManager
        DutchExchange dx = new DutchExchange(dutchExchangeAddress, web3, ctm1, gasProvider);
        DxInteracts dxi = new DxInteracts(dxInteractsAddress, web3, ctm1, gasProvider);
        TokenGNO gno = new TokenGNO(gnoAddress, web3, ctm1, gasProvider);
        // EtherToken weth = new EtherToken(wethAddress, web3, ctm1, gasProvider);
        
        // 20 ether
        var startingETH = toWei(20L);
        // 50e18 GNO tokens
        var startingGNO = toWei(50L);

        // Deposit GNO into the DutchExchange
        gno.approve(dx.getContractAddress(), startingGNO).send();
        dx.deposit(gno.getContractAddress(), startingGNO).send();

        // Deposit 20 Ether into the DutchExchange as WETH (dxi converts it for you)
        dxi.depositEther(startingETH).send();
        
        var startBlock = DefaultBlockParameter.valueOf("earliest");
        var endBlock = DefaultBlockParameter.valueOf("latest");
        
        dx.newTokenPairEventFlowable(startBlock, endBlock).subscribe(e -> {
            String result = "New Token Pair." + System.lineSeparator() + 
                            "buy token: " + contractName.get(e.buyToken.toUpperCase()) + ", " +
                            "sell token: " + contractName.get(e.sellToken.toUpperCase());
            System.out.println(System.lineSeparator() + result);
            
        });
        
        dx.newSellOrderEventFlowable(startBlock, endBlock).subscribe(e -> {
            String result = "New Sell Order." + System.lineSeparator() + 
                            "buy token: " + contractName.get(e.buyToken.toUpperCase()) + ", " +
                            "sell token: " + contractName.get(e.sellToken.toUpperCase()) + ", " +
                            "amount: " + e.amount + ", " +
                            "user: " + e.user;
            System.out.println(System.lineSeparator() + result);
        });
        
        dx.newBuyOrderEventFlowable(startBlock, endBlock).subscribe(e -> {
            String result = "New Buy Order." + System.lineSeparator() + 
                            "buy token: " + contractName.get(e.buyToken.toUpperCase()) + ", " +
                            "sell token: " + contractName.get(e.sellToken.toUpperCase()) + ", " +
                            "amount: " + e.amount + ", " +
                            "user: " + e.user;
            System.out.println(System.lineSeparator() + result); 
        });
        
        // This prints out all dutchX deposits. Suitable for dev environment
        dx.newDepositEventFlowable(startBlock, endBlock).subscribe(e -> {
            String result = "DutchExchange deposit." + System.lineSeparator() + 
                            "token: " + contractName.get(e.token.toUpperCase()) + ", " +
                            "amount: " + e.amount;
            System.out.println(System.lineSeparator() + result);
        });

        dx.auctionClearedEventFlowable(startBlock, endBlock).subscribe(e -> {
            // if its our auction
            // TODO: check if sell or buy volume
            dx.claimAndWithdraw(e.sellToken, e.buyToken, dxi.getContractAddress(), e.auctionIndex, e.sellVolume).send();
        });
        
        // Add token pair WETH <-> GNO on DutchExchange
        var token1Funding = toWei(10L);
        var token2Funding = BigInteger.valueOf(0L);
        var initialClosingPriceNum = BigInteger.valueOf(2L);
        var initialClosingPriceDen = BigInteger.valueOf(1L);
        dxi.addTokenPair(wethAddress, gnoAddress, token1Funding, token2Funding, initialClosingPriceNum, initialClosingPriceDen).send();
        
        // Post WETH sell order on auction
        var auctionIndex = dx.getAuctionIndex(wethAddress, gnoAddress).send();
        var sellOrderAmount = BigInteger.valueOf(10000L);
        var buyOrderAmount = BigInteger.valueOf(10000L);
        dxi.postSellOrder(wethAddress, gnoAddress, auctionIndex, sellOrderAmount).send();
        
        // Skip evm time ~6hrs for auction to open
        evmSkipTime(22000);
        
        dx.postBuyOrder(wethAddress, gnoAddress, auctionIndex, buyOrderAmount).send();
    }
}
