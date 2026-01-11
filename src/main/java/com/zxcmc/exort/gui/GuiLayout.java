package com.zxcmc.exort.gui;

public final class GuiLayout {
    public static final int INVENTORY_SIZE = 54;
    public static final int PAGE_SIZE = 45;

    public static final class Storage {
        public static final int SLOT_PREV = 45;
        public static final int SLOT_NEXT = 53;
        public static final int SLOT_SORT = 47;
        public static final int SLOT_INFO = 51;
        public static final int SLOT_SEARCH = 49;

        private Storage() {
        }
    }

    public static final class Crafting {
        public static final int SLOT_PREV = 45;
        public static final int SLOT_NEXT = 49;
        public static final int SLOT_OUTPUT = 43;
        public static final int SLOT_CLEAR = 35;
        public static final int SLOT_STORAGE_CRAFT = 42;
        public static final int SLOT_PLAYER_CRAFT = 52;
        public static final int SLOT_SORT = 46;
        public static final int SLOT_INFO = 48;
        public static final int SLOT_SEARCH = 47;

        private Crafting() {
        }
    }

    public static final class Bus {
        public static final int SIZE = 18;
        public static final int SLOT_MODE = 1;
        public static final int SLOT_INFO = 10;
        public static final int[] FILTER_SLOTS = new int[]{3, 4, 5, 6, 7, 12, 13, 14, 15, 16};

        private Bus() {
        }
    }

    private GuiLayout() {
    }
}
