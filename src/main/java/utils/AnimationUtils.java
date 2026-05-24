package utils;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Utility class for consistent UI animations across BidHub.
 */
public final class AnimationUtils {

    private static final double FADE_OUT_MS = 120;
    private static final double FADE_IN_MS = 200;
    private static final double SLIDE_IN_MS = 250;
    private static final double SLIDE_Y_OFFSET = 10;

    private AnimationUtils() {
        // Utility class
    }

    /**
     * Performs a smooth transition between two views.
     *
     * @param oldView The view to hide.
     * @param newView The view to show.
     * @param onFinished Callback after animation completes.
     */
    public static void switchView(Node oldView, Node newView, Runnable onFinished) {
        if (oldView == null || newView == null) {
            if (oldView != null) {
                oldView.setVisible(false);
                oldView.setManaged(false);
            }
            if (newView != null) {
                newView.setVisible(true);
                newView.setManaged(true);
            }
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }

        // Prepare next view
        newView.setOpacity(0);
        newView.setTranslateY(SLIDE_Y_OFFSET);
        newView.setVisible(true);
        newView.setManaged(true);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_MS), oldView);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_MS), newView);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(SLIDE_IN_MS), newView);
        slideIn.setFromY(SLIDE_Y_OFFSET);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        fadeOut.setOnFinished(e -> {
            oldView.setVisible(false);
            oldView.setManaged(false);
            oldView.setOpacity(1.0);
            oldView.setTranslateY(0);

            ParallelTransition parallel = new ParallelTransition(fadeIn, slideIn);
            if (onFinished != null) {
                parallel.setOnFinished(pe -> onFinished.run());
            }
            parallel.play();
        });

        fadeOut.play();
    }

    /**
     * Animates an accordion-style expansion/collapse.
     *
     * @param pane The pane to animate.
     * @param arrow The arrow icon to rotate.
     * @param expand Whether to expand or collapse.
     * @param maxHeight The target maximum height for expansion.
     */
    public static void animateAccordion(Node pane, Node arrow, boolean expand, double maxHeight) {
        if (pane == null) {
            return;
        }

        if (expand) {
            pane.setManaged(true);
            pane.setVisible(true);
            pane.setOpacity(0);
            // Height animation would require a timeline if using dynamic height,
            // but for simplicity and consistency with existing code:
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), pane);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        } else {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), pane);
            fadeOut.setFromValue(pane.getOpacity());
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                pane.setManaged(false);
                pane.setVisible(false);
            });
            fadeOut.play();
        }

        if (arrow != null) {
            arrow.setRotate(expand ? 180 : 0);
        }
    }

    /**
     * Simple fade in animation.
     */
    public static void fadeIn(Node node) {
        if (node == null) {
            return;
        }
        node.setVisible(true);
        node.setManaged(true);
        FadeTransition ft = new FadeTransition(Duration.millis(FADE_IN_MS), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }
}
