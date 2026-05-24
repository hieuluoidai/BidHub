package utils;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;
import model.auction.Auction;

import java.time.LocalDateTime;

/**
 * Tiện ích quản lý đồng hồ đếm ngược cho phiên đấu giá trên giao diện.
 */
public class AuctionTimer {

    private final Label lblCountdown;
    private final ProgressBar progressBar;
    private Timeline timeline;
    private ScaleTransition pulseAnimation;
    private Auction auction;

    public AuctionTimer(Label lblCountdown, ProgressBar progressBar) {
        this.lblCountdown = lblCountdown;
        this.progressBar = progressBar;
    }

    public void start(Auction auction) {
        this.auction = auction;
        if (timeline != null) {
            timeline.stop();
        }
        stopPulseAnimation();
        if (auction == null || lblCountdown == null) return;

        timeline = new Timeline(
            new KeyFrame(Duration.seconds(1), event -> updateUI())
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        updateUI();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
        stopPulseAnimation();
    }

    private void updateUI() {
        if (auction == null || lblCountdown == null) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = auction.getStartTime();
        LocalDateTime end = auction.getEndTime();
        String status = auction.getStatus();

        if (status.equals("FINISHED") || status.equals("PAID") || status.equals("CANCELED") || now.isAfter(end)) {
            lblCountdown.setText("Đã kết thúc");
            lblCountdown.getStyleClass().remove("countdown-urgent");
            stopPulseAnimation();
            setProgressBar(0.0, false);
            if (timeline != null) timeline.stop();
            return;
        }

        if (status.equals("OPEN") && now.isBefore(start)) {
            java.time.Duration duration = java.time.Duration.between(now, start);
            lblCountdown.setText(String.format("%02d:%02d:%02d",
                    duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart()));
            stopPulseAnimation();
            setProgressBar(1.0, false);
            return;
        }

        java.time.Duration duration = java.time.Duration.between(now, end);
        lblCountdown.setText(String.format("%02d:%02d:%02d",
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart()));

        // FOMO effect (< 5 mins)
        if (duration.toMinutes() < 5) {
            if (!lblCountdown.getStyleClass().contains("countdown-urgent")) {
                lblCountdown.getStyleClass().add("countdown-urgent");
            }
        } else {
            lblCountdown.getStyleClass().remove("countdown-urgent");
        }

        // Pulse animation (< 30 secs)
        if (duration.toSeconds() >= 0 && duration.toSeconds() < 30) {
            if (pulseAnimation == null) {
                pulseAnimation = new ScaleTransition(Duration.millis(500), lblCountdown);
                pulseAnimation.setFromX(1.0);
                pulseAnimation.setToX(1.1);
                pulseAnimation.setFromY(1.0);
                pulseAnimation.setToY(1.1);
                pulseAnimation.setAutoReverse(true);
                pulseAnimation.setCycleCount(Animation.INDEFINITE);
                pulseAnimation.play();
            }
        } else {
            stopPulseAnimation();
        }

        // Progress bar
        if (start != null && end != null) {
            long totalSeconds = java.time.Duration.between(start, end).toSeconds();
            double progress = totalSeconds > 0
                    ? Math.max(0.0, (double) duration.toSeconds() / totalSeconds)
                    : 0.0;
            setProgressBar(progress, progress < 0.1);
        }
    }

    private void stopPulseAnimation() {
        if (pulseAnimation != null) {
            pulseAnimation.stop();
            pulseAnimation = null;
        }
        if (lblCountdown != null) {
            lblCountdown.setScaleX(1.0);
            lblCountdown.setScaleY(1.0);
        }
    }

    private void setProgressBar(double progress, boolean urgent) {
        if (progressBar == null) return;
        progressBar.setProgress(progress);
        progressBar.getStyleClass().remove("time-progress-urgent");
        if (urgent) {
            progressBar.getStyleClass().add("time-progress-urgent");
        }
    }
}
