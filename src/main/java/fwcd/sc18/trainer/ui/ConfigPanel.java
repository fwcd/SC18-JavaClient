package fwcd.sc18.trainer.ui;

import java.awt.Color;
import java.io.File;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.NumberEditor;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

/**
 * A configuration UI with a very convenient API.
 */
public class ConfigPanel {
	private static final JFileChooser FILE_CHOOSER = new JFileChooser();
	private final JComponent view;
	
	private JPanel currentSuperPanel;
	private JPanel currentPanel;
	
	private boolean horizontal;
	
	public ConfigPanel(boolean horizontal) {
		this(horizontal, true);
	}
	
	public ConfigPanel(boolean horizontal, boolean floatable) {
		this.horizontal = horizontal;
		view = new JToolBar(getOrientation());
		((JToolBar) view).setFloatable(floatable);
		view.setLayout(new BoxLayout(view, SwingConstants.VERTICAL));
		nextSuperSection();
	}
	
	private ConfigPanel() {
		view = new JPanel();
	}

	private int getOrientation() {
		return horizontal ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL;
	}

	private int getInverseOrientation() {
		return horizontal ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL;
	}
	
	private JComponent pack(JComponent component, String label) {
		if (label.length() > 0) {
			JPanel pane = new JPanel();
			pane.setLayout(new BoxLayout(pane, horizontal ? BoxLayout.X_AXIS : BoxLayout.Y_AXIS));
			pane.add(new JLabel(label + ": "));
			pane.add(component);
			return pane;
		} else {
			return component;
		}
	}
	
	private void addComponent(JComponent component) {
		currentPanel.add(component);
	}
	
	public ConfigPanel addSubPanel(String title) {
		ConfigPanel sub = new ConfigPanel();
		sub.horizontal = horizontal;
		sub.nextSuperSection();
		sub.view.setBorder(new TitledBorder(new LineBorder(Color.GRAY, 1), title));
		addComponent(sub.getView());
		
		return sub;
	}
	
	public void nextSection() {
		currentPanel = new JPanel();
		currentPanel.setLayout(new BoxLayout(currentPanel, getInverseOrientation()));
		currentSuperPanel.add(currentPanel);
	}
	
	public void nextSuperSection() {
		currentSuperPanel = new JPanel();
		currentSuperPanel.setLayout(new BoxLayout(currentSuperPanel, getOrientation()));
		view.add(currentSuperPanel);
		nextSection();
	}
	
	public IntSupplier addIntOption(String name, int defValue, int min, int max) {
		SpinnerModel model = new SpinnerNumberModel(defValue, min, max, 1);
		JSpinner spinner = new JSpinner(model);
		spinner.setEditor(new NumberEditor(spinner, "#"));
		addComponent(pack(spinner, name));
		
		return () -> (Integer) model.getValue();
	}
	
	public Supplier<String> addStringOption(String name, String defValue) {
		JTextField field = new JTextField(defValue);
		addComponent(pack(field, name));
		
		return field::getText;
	}
	
	public BooleanSupplier addBoolOption(String name, boolean defValue) {
		JCheckBox box = new JCheckBox();
		box.setSelected(defValue);
		addComponent(pack(box, name));
		
		return box::isSelected;
	}
	
	public Supplier<File> addFileOption(String name, String suffix) {
		return addFileOption(name, suffix, null, false);
	}
	
	private static class Holder<T> {
		private T item = null;
		
		public void set(T item) { this.item = item; }
		
		public T get() { return item; }
	}
	
	public Supplier<File> addFileOption(String name, String suffix, File defValue, boolean isFolder) {
		Holder<File> fileProperty = new Holder<>();
		JButton button = new JButton(name);
		
		try {
			if (defValue != null && defValue.exists()) {
				FILE_CHOOSER.setSelectedFile(defValue);
			}
		} catch (Exception e) {}
		
		button.setBackground(Color.ORANGE);
		button.setOpaque(true);
		button.setFocusPainted(false);
		button.addActionListener(l -> {
			FILE_CHOOSER.setFileSelectionMode(isFolder ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
			
			if (FILE_CHOOSER.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				handleFileSelection(suffix, fileProperty, button);
			}
		});
		handleFileSelection(suffix, fileProperty, button);
		
		addComponent(button);
		
		return fileProperty::get;
	}

	private void handleFileSelection(String suffix, Holder<File> fileProperty, JButton button) {
		File file = FILE_CHOOSER.getSelectedFile();
		
		if (file != null && file.getName().endsWith(suffix)) {
			fileProperty.set(file);
			button.setText(file.getAbsolutePath());
			button.setBackground(Color.GREEN.brighter());
		}
	}
	
	public void addButton(String title, Runnable action) {
		JButton button = new JButton(title);
		button.addActionListener(l -> action.run());
		addComponent(button);
	}
	
	public JComponent getView() {
		return view;
	}
}
