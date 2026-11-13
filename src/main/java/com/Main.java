package com;

import com.parser.ui.ParserGUI;

import javax.swing.*;


public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new ParserGUI().setVisible(true);
        });
    }
}