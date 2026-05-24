package com.zxcmc.exort.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExortPluginModeWarningTest {
  @Test
  void chorusFallbackWarningMentionsAutomaticFixCommand() {
    String warning = String.join("\n", ExortPlugin.chorusFallbackHelpLines());

    assertTrue(warning.contains(ExortPlugin.MODE_FIX_RESOURCE_COMMAND));
    assertTrue(warning.contains("This command will update the Paper option"));
    assertTrue(warning.contains("set Exort mode to RESOURCE"));
    assertTrue(warning.contains("restart the server after 10 seconds"));
    assertTrue(warning.contains("RESOURCE mode"));
  }
}
