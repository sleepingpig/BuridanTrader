package com.buridantrader;

import com.buridantrader.config.TradingConfig;
import com.buridantrader.exceptions.NoSuchPathException;
import com.buridantrader.exceptions.ValueLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CandidateAssetProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CandidateAssetProducer.class);
    private final AssetViewer assetViewer;
    private final PricePredictor pricePredictor;
    private final PriceConverter priceConverter;
    private final TradingConfig config;

    public CandidateAssetProducer(
            @Nonnull TradingConfig config,
            @Nonnull AssetViewer assetViewer,
            @Nonnull PricePredictor pricePredictor,
            @Nonnull PriceConverter priceConverter) {
        this.config = config;
        this.assetViewer = assetViewer;
        this.pricePredictor = pricePredictor;
        this.priceConverter = priceConverter;
    }

    @Nonnull
    public List<CandidateAsset> getCandidates() throws IOException {
        List<CandidateAsset> candidates = new ArrayList<>();
        for (Asset asset : assetViewer.getAccountAssets()) {

            BigDecimal freeQuantity = calFreeQuantity(asset);
            BigDecimal freeValue;
            try {
                freeValue = calValue(asset, freeQuantity);
            } catch (NoSuchPathException ex) {
                LOGGER.debug("The balance of asset {} is ignored because: {}",
                        asset.getCurrency(), ex.getMessage());
                continue;
            }

            PricePrediction prediction;
            try {
                prediction = pricePredictor.getPrediction(asset.getCurrency(), config.getQuoteCurrency());
            } catch (NoSuchPathException ex) {
                LOGGER.info("Unable to get prediction for {} relative to {}. Skipping it",
                        asset.getCurrency(),
                        config.getQuoteCurrency());
                continue;
            }

            LOGGER.debug("For asset {}, growth per sec: {}, profitable: {}, free quantity: {}, "
                            + "free value relative to {}: {}",
                    asset.getCurrency(),
                    prediction.getGrowthPerSec(),
                    prediction.isProfitable(),
                    freeQuantity,
                    config.getQuoteCurrency(),
                    freeValue);
            CandidateAsset candidate = new CandidateAsset(asset, prediction, freeQuantity, freeValue);
            candidate.setEligibleForSource(isEligibleForSource(candidate));
            candidates.add(candidate);
        }
        return candidates;
    }

    private boolean isEligibleForSource(@Nonnull CandidateAsset candidateAsset) {
        return candidateAsset.getFreeValue().compareTo(config.getMinTradingValue()) >= 0;
    }

    @Nonnull
    private BigDecimal calFreeQuantity(@Nonnull Asset asset) {
        BigDecimal balance = asset.getBalance();
        // Honoring the min preferred quantity, and do not trade more than that.
        BigDecimal minPreferredQuantity = config.getAssetConfig(asset.getCurrency()).getMinPreferredQuantity();
        if(minPreferredQuantity.compareTo(balance) >= 0) {
            return BigDecimal.ZERO;
        } else {
            return balance.subtract(minPreferredQuantity);
        }
    }

    @Nonnull
    private BigDecimal calValue(@Nonnull Asset asset, @Nonnull BigDecimal quantity)
            throws IOException, NoSuchPathException {
        try {
            return priceConverter.getRelativePrice(
                    asset.getCurrency(), config.getQuoteCurrency(), quantity);
        } catch (ValueLimitException ex) {
            return BigDecimal.ZERO;
        }
    }
}
