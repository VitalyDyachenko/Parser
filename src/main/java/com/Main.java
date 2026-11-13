package com;

import com.formdev.flatlaf.FlatDarkLaf;
import com.parser.ui.ParserGUI;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new ParserGUI().setVisible(true);
        });
    }
}