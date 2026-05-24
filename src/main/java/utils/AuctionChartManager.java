package utils;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.util.StringConverter;
import model.auction.Auction;
import model.auction.BidTransaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiện ích quản lý biểu đồ lịch sử giá cho phiên đấu giá.
 */
public class AuctionChartManager {

    private final AreaChart<Number, Number> chart;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;
    private static final int MAX_VISIBLE_BIDS = 10;

    public AuctionChartManager(AreaChart<Number, Number> chart, NumberAxis xAxis, NumberAxis yAxis) {
        this.chart = chart;
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        setupChart();
    }

    private void setupChart() {
        if (chart == null) return;
        
        chart.setAnimated(true);
        chart.setCreateSymbols(true);
        xAxis.setForceZeroInRange(false);
        xAxis.setTickUnit(1);
        xAxis.setMinorTickVisible(false);
        xAxis.setLabel("Lượt đặt giá");
        yAxis.setLabel("Giá ($)");

        xAxis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                return String.valueOf(object.intValue());
            }
            @Override
            public Number fromString(String string) {
                return Double.parseDouble(string);
            }
        });
    }

    public void updateChart(Auction auction) {
        if (chart == null || auction == null) return;

        chart.getData().clear();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Diễn biến giá");

        List<BidTransaction> history = auction.getBidHistory();
        List<Double> dataPoints = new ArrayList<>();
        dataPoints.add(auction.getItem().getStartingPrice());

        for (BidTransaction tx : history) {
            dataPoints.add(tx.getBidAmount());
        }

        int startIdx = Math.max(0, dataPoints.size() - MAX_VISIBLE_BIDS);
        for (int i = startIdx; i < dataPoints.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, dataPoints.get(i)));
        }

        chart.getData().add(series);

        // Tự động scale trục Y
        double min = dataPoints.subList(startIdx, dataPoints.size()).stream().min(Double::compare).orElse(0.0);
        double max = dataPoints.subList(startIdx, dataPoints.size()).stream().max(Double::compare).orElse(100.0);
        yAxis.setLowerBound(Math.max(0, min * 0.9));
        yAxis.setUpperBound(max * 1.1);
    }
}
