package com.parser.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.*;
import java.net.URL;

public class ParserGUI extends JFrame {

	private JTextPane textArea;
	private JTable errorTable;
	private DefaultTableModel tableModel;
	private JFileChooser fileChooser;
	private File currentFile;
	private boolean isModified = false;
	private UndoManager undoManager;
	private JLabel statusLabel;
	private String statusMessage = "Готово";

	public ParserGUI() {
		setTitle("Compiler - Текстовый редактор");
		setSize(900, 700);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setLocationRelativeTo(null);

		fileChooser = new JFileChooser();
		undoManager = new UndoManager();

		initUI();
		initMenu();
		initToolBar();

		// Обработка закрытия окна
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				exitApplication();
			}
		});
	}

	private void initUI() {
		// Область редактирования
		textArea = new JTextPane();
		textArea.getDocument().addUndoableEditListener(undoManager);
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { setModified(true); }
			public void removeUpdate(DocumentEvent e) { setModified(true); }
			public void changedUpdate(DocumentEvent e) { setModified(true); }
		});

		textArea.addCaretListener(e -> updateStatusBar());

		textArea.addMouseWheelListener(e -> {
			if (e.isControlDown()) {
				int newSize = textArea.getFont().getSize() - e.getWheelRotation();
				if (newSize >= 8 && newSize <= 72) {
					Font newFont = new Font(textArea.getFont().getName(), Font.PLAIN, newSize);
					textArea.setFont(newFont);
					errorTable.setFont(newFont);
					errorTable.setRowHeight(newSize + 4);
				}
			}
		});

		textArea.setDragEnabled(true);
		textArea.setTransferHandler(new TransferHandler() {
			@Override
			public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override
			public boolean importData(TransferSupport support) {
				if (!canImport(support)) return false;
				try {
					@SuppressWarnings("unchecked")
					java.util.List<File> files = (java.util.List<File>) support.getTransferable()
							.getTransferData(DataFlavor.javaFileListFlavor);
					if (!files.isEmpty() && checkUnsavedChanges()) {
						File file = files.get(0);
						String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
						textArea.setText(content);
						currentFile = file;
						setModified(false);
						setTitle("Compiler - " + currentFile.getName());
						setStatusMessage("Открыт: " + currentFile.getName());
						return true;
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				return false;
			}
		});

		JScrollPane editorScroll = new JScrollPane(textArea);

		// Область вывода результатов
		String[] columns = {"Путь", "Строка", "Столбец", "Сообщение"};
		tableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		errorTable = new JTable(tableModel);
		errorTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int row = errorTable.getSelectedRow();
				if (row >= 0) {
					try {
						int line = Integer.parseInt(tableModel.getValueAt(row, 1).toString());
						javax.swing.text.Element root = textArea.getDocument().getDefaultRootElement();
						if (line > 0 && line <= root.getElementCount()) {
							int pos = root.getElement(line - 1).getStartOffset();
							textArea.setCaretPosition(pos);
							textArea.requestFocus();
						}
					} catch (Exception ex) {
					}
				}
			}
		});
		JScrollPane outputScroll = new JScrollPane(errorTable);

		// Разделитель
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, outputScroll);
		splitPane.setResizeWeight(0.75);
		add(splitPane, BorderLayout.CENTER);

		// Строка состояния (доп. требование)
		statusLabel = new JLabel(" Готово");
		add(statusLabel, BorderLayout.SOUTH);
	}

	private void updateStatusBar() {
		try {
			int pos = textArea.getCaretPosition();
			javax.swing.text.Element root = textArea.getDocument().getDefaultRootElement();
			int line = root.getElementIndex(pos) + 1;
			int col = pos - root.getElement(line - 1).getStartOffset() + 1;
			statusLabel.setText(" " + statusMessage + " | Строка: " + line + ", Столбец: " + col);
		} catch (Exception ex) {
			statusLabel.setText(" " + statusMessage);
		}
	}

	private void setStatusMessage(String message) {
		this.statusMessage = message;
		updateStatusBar();
	}

	private void initMenu() {
		JMenuBar menuBar = new JMenuBar();

		// --- Меню Файл ---
		JMenu fileMenu = new JMenu("Файл");
		JMenuItem createItem = new JMenuItem("Создать");
		JMenuItem openItem = new JMenuItem("Открыть");
		JMenuItem saveItem = new JMenuItem("Сохранить");
		JMenuItem saveAsItem = new JMenuItem("Сохранить как");
		JMenuItem exitItem = new JMenuItem("Выход");

		createItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

		createItem.addActionListener(e -> newFile());
		openItem.addActionListener(e -> openFile());
		saveItem.addActionListener(e -> saveFile(false));
		saveAsItem.addActionListener(e -> saveFile(true));
		exitItem.addActionListener(e -> exitApplication());

		fileMenu.add(createItem);
		fileMenu.add(openItem);
		fileMenu.addSeparator();
		fileMenu.add(saveItem);
		fileMenu.add(saveAsItem);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);

		// --- Меню Правка ---
		JMenu editMenu = new JMenu("Правка");
		JMenuItem undoItem = new JMenuItem("Отменить");
		JMenuItem redoItem = new JMenuItem("Повторить");
		JMenuItem cutItem = new JMenuItem("Вырезать");
		JMenuItem copyItem = new JMenuItem("Копировать");
		JMenuItem pasteItem = new JMenuItem("Вставить");
		JMenuItem deleteItem = new JMenuItem("Удалить");
		JMenuItem selectAllItem = new JMenuItem("Выделить все");

		undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
		redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
		cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
		copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
		pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
		deleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));

		undoItem.addActionListener(e -> { if (undoManager.canUndo()) undoManager.undo(); });
		redoItem.addActionListener(e -> { if (undoManager.canRedo()) undoManager.redo(); });
		cutItem.addActionListener(e -> textArea.cut());
		copyItem.addActionListener(e -> textArea.copy());
		pasteItem.addActionListener(e -> textArea.paste());
		deleteItem.addActionListener(e -> textArea.replaceSelection(""));
		selectAllItem.addActionListener(e -> textArea.selectAll());

		editMenu.add(undoItem);
		editMenu.add(redoItem);
		editMenu.addSeparator();
		editMenu.add(cutItem);
		editMenu.add(copyItem);
		editMenu.add(pasteItem);
		editMenu.add(deleteItem);
		editMenu.addSeparator();
		editMenu.add(selectAllItem);

		// --- Меню Текст ---
		JMenu textMenu = new JMenu("Текст");
		String[] textItems = {"Постановка задачи", "Грамматика", "Классификация грамматики", "Метод анализа", "Тестовый пример", "Список литературы", "Исходный код программы"};
		for (String item : textItems) {
			JMenuItem menuItem = new JMenuItem(item);
			menuItem.addActionListener(e -> JOptionPane.showMessageDialog(this, "Раздел в разработке: " + item, "Информация", JOptionPane.INFORMATION_MESSAGE));
			textMenu.add(menuItem);
		}

		// --- Меню Пуск ---
		JMenu runMenu = new JMenu("Пуск");
		JMenuItem runItem = new JMenuItem("Запуск анализатора");
		runItem.addActionListener(e -> JOptionPane.showMessageDialog(this, "Анализатор будет добавлен позже", "В разработке", JOptionPane.WARNING_MESSAGE));
		runMenu.add(runItem);

		// --- Меню Справка ---
		JMenu helpMenu = new JMenu("Справка");
		JMenuItem helpItem = new JMenuItem("Вызов справки");
		JMenuItem aboutItem = new JMenuItem("О программе");

		helpItem.addActionListener(e -> showHelpDialog());
		aboutItem.addActionListener(e -> showAboutDialog());

		helpMenu.add(helpItem);
		helpMenu.add(aboutItem);

		// Сборка меню
		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		menuBar.add(textMenu);
		menuBar.add(runMenu);
		menuBar.add(helpMenu);
		setJMenuBar(menuBar);
	}

	private void initToolBar() {
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);

		// Создание кнопок
		JButton btnNew = new JButton("Создать");
		JButton btnOpen = new JButton("Открыть");
		JButton btnSave = new JButton("Сохранить");
		JButton btnUndo = new JButton("Отменить");
		JButton btnRedo = new JButton("Повторить");
		JButton btnCopy = new JButton("Копировать");
		JButton btnCut = new JButton("Вырезать");
		JButton btnPaste = new JButton("Вставить");
		JButton btnRun = new JButton("Запуск");
		JButton btnHelp = new JButton("Справка");
		JButton btnAbout = new JButton("О программе");

		// Привязка действий
		btnNew.addActionListener(e -> newFile());
		btnOpen.addActionListener(e -> openFile());
		btnSave.addActionListener(e -> saveFile(false));
		btnUndo.addActionListener(e -> { if (undoManager.canUndo()) undoManager.undo(); });
		btnRedo.addActionListener(e -> { if (undoManager.canRedo()) undoManager.redo(); });
		btnCopy.addActionListener(e -> textArea.copy());
		btnCut.addActionListener(e -> textArea.cut());
		btnPaste.addActionListener(e -> textArea.paste());
		btnRun.addActionListener(e -> JOptionPane.showMessageDialog(this, "Анализатор в разработке"));
		btnHelp.addActionListener(e -> showHelpDialog());
		btnAbout.addActionListener(e -> showAboutDialog());

		toolBar.add(btnNew);
		toolBar.add(btnOpen);
		toolBar.add(btnSave);
		toolBar.addSeparator();
		toolBar.add(btnUndo);
		toolBar.add(btnRedo);
		toolBar.addSeparator();
		toolBar.add(btnCopy);
		toolBar.add(btnCut);
		toolBar.add(btnPaste);
		toolBar.addSeparator();
		toolBar.add(btnRun);
		toolBar.addSeparator();
		toolBar.add(btnHelp);
		toolBar.add(btnAbout);

		add(toolBar, BorderLayout.NORTH);
	}

	private void showHelpDialog() {
		URL helpUrl = getClass().getResource("/help.html");

		if (helpUrl == null) {
			JOptionPane.showMessageDialog(this, "Файл справки не найден!", "Ошибка", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			JEditorPane editorPane = new JEditorPane();
			editorPane.setEditable(false);
			editorPane.setPage(helpUrl);

			JScrollPane scrollPane = new JScrollPane(editorPane);
			scrollPane.setPreferredSize(new Dimension(500, 450));

			JOptionPane.showMessageDialog(this, scrollPane, "Справка", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Ошибка при загрузке справки: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

	private void showAboutDialog() {
		String aboutText = "<html><body style='width: 250px; text-align: center; font-family: sans-serif;'>"
				+ "<h2>Текстовый редактор</h2>"
				+ "<p><b>Версия 1.0</b></p>"
				+ "<p>Разработан в рамках лабораторной работы.</p>"
				+ "<p>В дальнейшем будет дополнен функциями языкового процессора.</p>"
				+ "</body></html>";

		JOptionPane.showMessageDialog(this, aboutText, "О программе", JOptionPane.INFORMATION_MESSAGE);
	}

	// --- Логика работы с файлами ---

	private void newFile() {
		if (checkUnsavedChanges()) {
			textArea.setText("");
			currentFile = null;
			setModified(false);
			setTitle("Compiler - Новый документ");
			setStatusMessage("Новый документ");
		}
	}

	private void openFile() {
		if (!checkUnsavedChanges()) return;

		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			currentFile = fileChooser.getSelectedFile();
			try {
				String content = new String(java.nio.file.Files.readAllBytes(currentFile.toPath()));
				textArea.setText(content);
				setModified(false);
				setTitle("Compiler - " + currentFile.getName());
				setStatusMessage("Открыт: " + currentFile.getName());
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Ошибка при открытии файла", "Ошибка", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void saveFile(boolean saveAs) {
		if (currentFile == null || saveAs) {
			if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				currentFile = fileChooser.getSelectedFile();
			} else {
				return;
			}
		}

		try {
			java.nio.file.Files.write(currentFile.toPath(), textArea.getText().getBytes());
			setModified(false);
			setTitle("Compiler - " + currentFile.getName());
			setStatusMessage("Сохранено: " + currentFile.getName());
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Ошибка при сохранении файла", "Ошибка", JOptionPane.ERROR_MESSAGE);
		}
	}

	private boolean checkUnsavedChanges() {
		if (isModified) {
			int result = JOptionPane.showConfirmDialog(this,
					"Документ был изменен. Сохранить изменения?",
					"Несохраненные изменения",
					JOptionPane.YES_NO_CANCEL_OPTION);

			if (result == JOptionPane.YES_OPTION) {
				saveFile(false);
				return !isModified;
			} else if (result == JOptionPane.CANCEL_OPTION) {
				return false;
			}
		}
		return true;
	}

	private void setModified(boolean modified) {
		this.isModified = modified;
	}

	private void exitApplication() {
		if (checkUnsavedChanges()) {
			System.exit(0);
		}
	}
}