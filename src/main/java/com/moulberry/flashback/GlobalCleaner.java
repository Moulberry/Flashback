package com.moulberry.flashback;

import java.lang.ref.Cleaner;

public class GlobalCleaner {
    public static final Cleaner INSTANCE = Cleaner.create();
}
