package com.github.aeddddd.ae2enhanced.container;

final class SplicedActionRouter {
    private SplicedActionRouter() {
    }

    static Target route(boolean interfaceAvailable, boolean interfaceVisible, long providerId,
            int slot, int slotCount, boolean hosted) {
        if (interfaceAvailable && providerId != 0L) {
            return interfaceVisible ? Target.INTERFACE : Target.DROP;
        }
        if (slot >= 0 && slot < slotCount && !hosted) {
            return Target.DROP;
        }
        return Target.OMNI;
    }

    static boolean isActionAllowed(int panelMask, String action) {
        if ("PanelVisibility".equals(action)) {
            return true;
        }
        if ("ClearCrafting".equals(action)) {
            return SplicedTermModules.isVisible(panelMask, SplicedTermModules.CRAFTING);
        }
        switch (action) {
            case "CraftMode":
            case "Encode":
            case "ClearPattern":
            case "MultiplyByTwo":
            case "MultiplyByThree":
            case "DivideByTwo":
            case "DivideByThree":
            case "IncreaseByOne":
            case "DecreaseByOne":
            case "Substitute":
            case "Scroll":
                return SplicedTermModules.isVisible(panelMask, SplicedTermModules.PATTERN);
            default:
                return false;
        }
    }

    enum Target {
        OMNI,
        INTERFACE,
        DROP
    }
}
