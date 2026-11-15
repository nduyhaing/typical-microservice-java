package com.shopapp.inventory.viewmodel.stockhistory;

import com.shopapp.inventory.model.StockHistory;
import java.time.ZonedDateTime;

public record StockHistoryVm(Long id, String productName, Long adjustedQuantity, String createdBy,
                             ZonedDateTime createdOn, String note) {

    public static StockHistoryVm fromModel(StockHistory history, String productName) {
        return new StockHistoryVm(history.getId(), productName, history.getAdjustedQuantity(),
            history.getCreatedBy(), history.getCreatedOn(), history.getNote());
    }
}
