package com.egogame.vehiclehailer.action;

/**
 * 延迟等待动作
 * 对应鱼蛋的 VehicleDelayAction，但增加随机延迟选项
 */
public class DelayAction extends VehicleActionBase {

    private long delayMs = 1000;        // 延迟毫秒数
    private boolean randomDelay = false; // 是否启用随机延迟（鱼蛋没有）
    private long randomMinMs = 500;     // 随机延迟最小值
    private long randomMaxMs = 3000;    // 随机延迟最大值

    public DelayAction() {
        super("延迟");
    }

    public DelayAction(long delayMs) {
        super("延迟");
        this.delayMs = Math.max(0, delayMs);
    }

    public DelayAction(long minMs, long maxMs) {
        super("随机延迟");
        this.randomDelay = true;
        this.randomMinMs = Math.max(0, minMs);
        this.randomMaxMs = Math.max(this.randomMinMs, maxMs);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.DELAY;
    }

    @Override
    public String getDescription() {
        if (randomDelay) {
            return "随机延迟 " + randomMinMs + "~" + randomMaxMs + "ms";
        }
        return "延迟 " + delayMs + "ms";
    }

    @Override
    public boolean execute() {
        try {
            long actualDelay = randomDelay
                    ? randomMinMs + (long)(Math.random() * (randomMaxMs - randomMinMs))
                    : delayMs;
            Thread.sleep(actualDelay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---- Getter/Setter ----

    public long getDelayMs() { return delayMs; }
    public void setDelayMs(long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    public boolean isRandomDelay() { return randomDelay; }
    public void setRandomDelay(boolean randomDelay) { this.randomDelay = randomDelay; }

    public long getRandomMinMs() { return randomMinMs; }
    public void setRandomMinMs(long randomMinMs) {
        this.randomMinMs = Math.max(0, randomMinMs);
    }

    public long getRandomMaxMs() { return randomMaxMs; }
    public void setRandomMaxMs(long randomMaxMs) {
        this.randomMaxMs = Math.max(this.randomMinMs, randomMaxMs);
    }
}
