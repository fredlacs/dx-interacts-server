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
import java.util.Map;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.http.HttpService;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import dxi.contracts.DutchExchange;
import dxi.contracts.DxInteracts;
import dxi.contracts.EtherToken;
import dxi.contracts.TokenGNO;


public class App {
    private static final Web3j web3 = Web3j.build(new HttpService());  // defaults to http://localhost:8545/
    private static final StaticGasProvider gasProvider = new StaticGasProvider(BigInteger.ZERO, DefaultGasProvider.GAS_LIMIT);
    
    private static final String dutchExchangeAddress = "0x2a504B5e7eC284ACa5b6f49716611237239F0b97";
    private static final String dxInteractsAddress = "0x4e71920b7330515faf5EA0c690f1aD06a85fB60c";
    private static final String wethAddress = "0xf204a4Ef082f5c04bB89F7D5E6568B796096735a";
    private static final String gnoAddress = "0x2C2B9C9a4a25e24B174f26114e8926a9f2128FE4";
    
    public static List<String> getAccounts() throws IOException {
        return web3.ethAccounts().send().getAccounts();
    }
    
    private static BigInteger toWei(Long ethValue) {
        BigInteger weiValue = BigInteger.valueOf(ethValue).multiply(BigInteger.valueOf(10).pow(18));
        return weiValue;
    }
    
    private static void evmSkipTime(Integer seconds) throws IOException {
        new Request("evm_increaseTime", Arrays.asList(seconds), new HttpService(), Response.class).send();
        new Request("evm_mine", Collections.EMPTY_LIST, new HttpService(), Response.class).send();
    }
    
    private static BigInteger getOutstandingVolume(DutchExchange dx, String sellTokenAddress, String buyTokenAddress, BigInteger auctionIndex) throws Exception {
        BigInteger sellVolume = dx.sellVolumesCurrent(sellTokenAddress, buyTokenAddress).send();
        BigInteger buyVol = dx.buyVolumes(sellTokenAddress, buyTokenAddress).send();
        Tuple2<BigInteger, BigInteger> priceFraction = dx.getCurrentAuctionPrice(sellTokenAddress, buyTokenAddress, auctionIndex).send();
        BigInteger price = priceFraction.getValue1().divide(priceFraction.getValue2());
        
        BigInteger outstandingVolume = sellVolume.multiply(price).subtract(buyVol);
        
        return outstandingVolume;
    }
    
    private static void activateLogs(DutchExchange dx) {
        
        Map<String, String> contractName = new HashMap<String, String>() {{
            put(dutchExchangeAddress.toUpperCase(), "DutchExchange");
            put(dxInteractsAddress.toUpperCase(), "DxInteracts");
            put(wethAddress.toUpperCase(), "WETH");
            put(gnoAddress.toUpperCase(), "GNO");
        }};
        
        DefaultBlockParameter startBlock = DefaultBlockParameter.valueOf("earliest");
        DefaultBlockParameter endBlock = DefaultBlockParameter.valueOf("latest");
        
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
            System.out.println("auction cleared");
        });
        
        dx.newSellerFundsClaimEventFlowable(startBlock, endBlock).subscribe(e -> {
            System.out.println("seller claimed: " + e.amount); 
        });
        
        dx.newBuyerFundsClaimEventFlowable(startBlock, endBlock).subscribe(e -> {
            System.out.println("buyer claimed: " + e.amount); 
        });
    
        dx.newWithdrawalEventFlowable(startBlock, endBlock).subscribe(e -> {
            System.out.println("withdrawn: " + e.amount + " of " + contractName.get(e.token.toUpperCase()));
        });
    }

    public static void main(String[] args) throws Exception {
        List<String> accounts = getAccounts();
        ClientTransactionManager ctm1 = new ClientTransactionManager(web3, accounts.get(0));
        // ClientTransactionManager ctm2 = new ClientTransactionManager(web3, accounts.get(1));
        
        // to call functions with different accounts, choose a different ClientTransactionManager
        DutchExchange dx = DutchExchange.load(dutchExchangeAddress, web3, ctm1, gasProvider);
        DxInteracts dxi = DxInteracts.load(dxInteractsAddress, web3, ctm1, gasProvider);
        TokenGNO gno = TokenGNO.load(gnoAddress, web3, ctm1, gasProvider);
        EtherToken weth = EtherToken.load(wethAddress, web3, ctm1, gasProvider);
        
        // 20 ether
        BigInteger startingETH = toWei(20L);
        // 50e18 GNO tokens
        BigInteger startingGNO = toWei(50L);
        
        // Deposit GNO into the DutchExchange
        gno.approve(dx.getContractAddress(), startingGNO).send();
        dx.deposit(gno.getContractAddress(), startingGNO).send();
        
        // Deposit 20 Ether into the DutchExchange as WETH (dxi converts it for you)
        dxi.depositEther(startingETH).send();
        
        activateLogs(dx);
    
        // Add token pair WETH <-> GNO on DutchExchange
        BigInteger token1Funding = toWei(10L);
        BigInteger token2Funding = BigInteger.valueOf(0L);
        BigInteger initialClosingPriceNum = BigInteger.valueOf(2L);
        BigInteger initialClosingPriceDen = BigInteger.valueOf(1L);
        // dx.withdraw(gnoAddress, BigInteger.valueOf(9950L)).send();
        dxi.addTokenPair(wethAddress, gnoAddress, token1Funding, token2Funding, initialClosingPriceNum, initialClosingPriceDen).send();
        
        // Post WETH sell order on auction
        BigInteger auctionIndex = dx.getAuctionIndex(wethAddress, gnoAddress).send();
        System.out.println("here: " + getOutstandingVolume(dx, wethAddress, gnoAddress, auctionIndex).toString());
        BigInteger sellOrderAmount = BigInteger.valueOf(10000L);
        dxi.postSellOrder(wethAddress, gnoAddress, auctionIndex, sellOrderAmount).send();
        
        // Skip evm time ~6hrs for auction to open
        evmSkipTime(22000);
        
        BigInteger buyOrderAmount = BigInteger.valueOf(10000L);
        dx.postBuyOrder(wethAddress, gnoAddress, auctionIndex, buyOrderAmount).send();
        
        evmSkipTime(2200000);        
        
        System.out.println("here: " + getOutstandingVolume(dx, wethAddress, gnoAddress, auctionIndex).toString());
        // sellerBalances[sellToken][buyToken][auctionIndex][user]
        
        System.out.println("gno balance in dx: " + dx.balances(gnoAddress, dxi.getContractAddress()).send().toString());
        dxi.claimSellerFunds(wethAddress, gnoAddress, dxi.getContractAddress(), auctionIndex).send();
        System.out.println("gno balance in dx: " + dx.balances(gnoAddress, dxi.getContractAddress()).send().toString());
        
        
        // System.out.println("gno balance: " + gno.balanceOf(dxi.getContractAddress()).send().toString());
        // dxi.withdraw(gnoAddress, BigInteger.valueOf(9950L)).send();
        // System.out.println("gno balance: " + gno.balanceOf(dxi.getContractAddress()).send().toString());
        
        
        System.out.println("weth balance in dx: " + dx.balances(wethAddress, accounts.get(0)).send().toString());
        // dxi.claimSellerFunds(wethAddress, gnoAddress, dxi.getContractAddress(), auctionIndex).send();
        System.out.println("weth balance in dx: " + dx.balances(wethAddress, accounts.get(0)).send().toString());
        
    
        System.out.println("buyer funds: " + dx.buyerBalances(wethAddress, gnoAddress, auctionIndex, accounts.get(0)).send().toString());
        // dxi.claimAuction(wethAddress, gnoAddress, dxi.getContractAddress(), auctionIndex, BigInteger.valueOf(100)).send();
        dx.claimBuyerFunds(wethAddress, gnoAddress, accounts.get(0), auctionIndex).send();
        System.out.println("buyer funds: " + dx.buyerBalances(wethAddress, gnoAddress, auctionIndex, accounts.get(0)).send().toString());
        
        System.out.println("weth balance: " + weth.balanceOf(accounts.get(0)).send().toString());
        dx.withdraw(gnoAddress, BigInteger.valueOf(9950L)).send();
        System.out.println("weth balance: " + weth.balanceOf(accounts.get(0)).send().toString());
    }
    
}
