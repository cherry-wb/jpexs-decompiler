/*
 *  Copyright (C) 2010-2015 JPEXS
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpexs.decompiler.flash.gui.dumpview;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.SWFInputStream;
import com.jpexs.decompiler.flash.abc.ABC;
import com.jpexs.decompiler.flash.abc.ABCInputStream;
import com.jpexs.decompiler.flash.abc.avm2.AVM2Code;
import com.jpexs.decompiler.flash.action.Action;
import com.jpexs.decompiler.flash.action.ActionListReader;
import com.jpexs.decompiler.flash.action.model.ConstantPool;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.dumpview.DumpInfo;
import com.jpexs.decompiler.flash.dumpview.DumpInfoSwfNode;
import com.jpexs.decompiler.flash.gui.Main;
import com.jpexs.decompiler.flash.gui.MainPanel;
import com.jpexs.decompiler.flash.gui.View;
import com.jpexs.helpers.Helper;
import com.jpexs.helpers.MemoryInputStream;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicLabelUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 *
 * @author JPEXS
 */
public class DumpTree extends JTree implements ActionListener {

    private static final String ACTION_CLOSE_SWF = "CLOSESWF";

    private static final String ACTION_EXPAND_RECURSIVE = "EXPANDRECURSIVE";

    private static final String ACTION_SAVE_TO_FILE = "SAVETOFILE";

    private static final String ACTION_PARSE_ACTIONS = "PARSEACTIONS";

    private static final String ACTION_PARSE_ABC = "PARSEABC";

    private static final String ACTION_PARSE_INSTRUCTIONS = "PARSEINSTRUCTIONS";

    private final MainPanel mainPanel;

    public class DumpTreeCellRenderer extends DefaultTreeCellRenderer {

        public DumpTreeCellRenderer() {
            setUI(new BasicLabelUI());
            setOpaque(false);
            setBackgroundNonSelectionColor(Color.white);
        }
    }

    public DumpTree(DumpTreeModel treeModel, MainPanel mainPanel) {
        super(treeModel);
        this.mainPanel = mainPanel;
        setCellRenderer(new DumpTreeCellRenderer());
        setRootVisible(false);
        setBackground(Color.white);
    }

    public void createContextMenu() {
        final JPopupMenu contextPopupMenu = new JPopupMenu();

        final JMenuItem expandRecursiveMenuItem = new JMenuItem(mainPanel.translate("contextmenu.expandAll"));
        expandRecursiveMenuItem.addActionListener(this);
        expandRecursiveMenuItem.setActionCommand(ACTION_EXPAND_RECURSIVE);
        contextPopupMenu.add(expandRecursiveMenuItem);

        final JMenuItem saveToFileMenuItem = new JMenuItem(mainPanel.translate("contextmenu.saveToFile"));
        saveToFileMenuItem.addActionListener(this);
        saveToFileMenuItem.setActionCommand(ACTION_SAVE_TO_FILE);
        contextPopupMenu.add(saveToFileMenuItem);

        final JMenuItem closeSelectionMenuItem = new JMenuItem(mainPanel.translate("contextmenu.closeSwf"));
        closeSelectionMenuItem.setActionCommand(ACTION_CLOSE_SWF);
        closeSelectionMenuItem.addActionListener(this);
        contextPopupMenu.add(closeSelectionMenuItem);

        final JMenuItem parseActionsMenuItem = new JMenuItem(mainPanel.translate("contextmenu.parseActions"));
        parseActionsMenuItem.setActionCommand(ACTION_PARSE_ACTIONS);
        parseActionsMenuItem.addActionListener(this);
        contextPopupMenu.add(parseActionsMenuItem);

        final JMenuItem parseAbcMenuItem = new JMenuItem(mainPanel.translate("contextmenu.parseABC"));
        parseAbcMenuItem.setActionCommand(ACTION_PARSE_ABC);
        parseAbcMenuItem.addActionListener(this);
        contextPopupMenu.add(parseAbcMenuItem);

        final JMenuItem parseInstructionsMenuItem = new JMenuItem(mainPanel.translate("contextmenu.parseInstructions"));
        parseInstructionsMenuItem.setActionCommand(ACTION_PARSE_INSTRUCTIONS);
        parseInstructionsMenuItem.addActionListener(this);
        contextPopupMenu.add(parseInstructionsMenuItem);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {

                    int row = getClosestRowForLocation(e.getX(), e.getY());
                    int[] selectionRows = getSelectionRows();
                    if (!Helper.contains(selectionRows, row)) {
                        setSelectionRow(row);
                    }

                    TreePath[] paths = getSelectionPaths();
                    if (paths == null || paths.length == 0) {
                        return;
                    }
                    closeSelectionMenuItem.setVisible(false);
                    expandRecursiveMenuItem.setVisible(false);
                    saveToFileMenuItem.setVisible(false);
                    parseActionsMenuItem.setVisible(false);
                    parseAbcMenuItem.setVisible(false);
                    parseInstructionsMenuItem.setVisible(false);

                    if (paths.length == 1) {
                        DumpInfo treeNode = (DumpInfo) paths[0].getLastPathComponent();

                        if (treeNode instanceof DumpInfoSwfNode) {
                            closeSelectionMenuItem.setVisible(true);
                        }

                        if (treeNode.getEndByte() - treeNode.startByte > 3) {
                            saveToFileMenuItem.setVisible(true);
                        }

                        // todo honfika: do not use string names, because it has conflicts e.g with DefineFont.code
                        if (treeNode.name.equals("actionBytes") && treeNode.getChildCount() == 0) {
                            parseActionsMenuItem.setVisible(true);
                        }

                        if (treeNode.name.equals("abcBytes") && treeNode.getChildCount() == 0) {
                            parseAbcMenuItem.setVisible(true);
                        }

                        if (treeNode.name.equals("code") && treeNode.parent.name.equals("method_body") && treeNode.getChildCount() == 0) {
                            parseInstructionsMenuItem.setVisible(true);
                        }

                        TreeModel model = getModel();
                        expandRecursiveMenuItem.setVisible(model.getChildCount(treeNode) > 0);
                    }

                    contextPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case ACTION_EXPAND_RECURSIVE: {
                TreePath path = getSelectionPath();
                if (path == null) {
                    return;
                }
                View.expandTreeNodes(this, path, true);
            }
            break;
            case ACTION_SAVE_TO_FILE: {
                TreePath[] paths = getSelectionPaths();
                DumpInfo dumpInfo = (DumpInfo) paths[0].getLastPathComponent();
                JFileChooser fc = new JFileChooser();
                String selDir = Configuration.lastOpenDir.get();
                fc.setCurrentDirectory(new File(selDir));
                if (!selDir.endsWith(File.separator)) {
                    selDir += File.separator;
                }
                JFrame f = new JFrame();
                View.setWindowIcon(f);
                if (fc.showSaveDialog(f) == JFileChooser.APPROVE_OPTION) {
                    File sf = Helper.fixDialogFile(fc.getSelectedFile());
                    try (FileOutputStream fos = new FileOutputStream(sf)) {
                        byte[] data = DumpInfoSwfNode.getSwfNode(dumpInfo).getSwf().originalUncompressedData;
                        fos.write(data, (int) dumpInfo.startByte, (int) (dumpInfo.getEndByte() - dumpInfo.startByte + 1));
                    } catch (IOException ex) {
                        Logger.getLogger(DumpTree.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            break;
            case ACTION_PARSE_ACTIONS: {
                TreePath[] paths = getSelectionPaths();
                DumpInfo dumpInfo = (DumpInfo) paths[0].getLastPathComponent();
                SWF swf = DumpInfoSwfNode.getSwfNode(dumpInfo).getSwf();
                byte[] data = swf.originalUncompressedData;
                int prevLength = (int) dumpInfo.startByte;
                try {
                    SWFInputStream rri = new SWFInputStream(swf, data);
                    if (prevLength != 0) {
                        rri.seek(prevLength);
                    }
                    List<Action> actions = ActionListReader.getOriginalActions(rri, prevLength, (int) dumpInfo.getEndByte());
                    for (Action action : actions) {
                        DumpInfo di = new DumpInfo(action.toString(), "Action", null, action.getAddress(), action.getTotalActionLength());
                        di.parent = dumpInfo;
                        rri.dumpInfo = di;
                        rri.seek(action.getAddress());
                        rri.readAction(new ConstantPool());
                        dumpInfo.getChildInfos().add(di);
                    }
                    repaint();
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(DumpTree.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            break;
            case ACTION_PARSE_ABC: {
                TreePath[] paths = getSelectionPaths();
                DumpInfo dumpInfo = (DumpInfo) paths[0].getLastPathComponent();
                SWF swf = DumpInfoSwfNode.getSwfNode(dumpInfo).getSwf();
                byte[] data = swf.originalUncompressedData;
                int prevLength = (int) dumpInfo.startByte;
                try {
                    ABCInputStream ais = new ABCInputStream(new MemoryInputStream(data, 0, prevLength + (int) dumpInfo.lengthBytes));
                    ais.seek(prevLength);
                    ais.dumpInfo = dumpInfo;
                    new ABC(ais, swf, null);
                } catch (IOException ex) {
                    Logger.getLogger(DumpTree.class.getName()).log(Level.SEVERE, null, ex);
                }
                repaint();
            }
            break;
            case ACTION_PARSE_INSTRUCTIONS: {
                TreePath[] paths = getSelectionPaths();
                DumpInfo dumpInfo = (DumpInfo) paths[0].getLastPathComponent();
                SWF swf = DumpInfoSwfNode.getSwfNode(dumpInfo).getSwf();
                byte[] data = swf.originalUncompressedData;
                int prevLength = (int) dumpInfo.startByte;
                try {
                    ABCInputStream ais = new ABCInputStream(new MemoryInputStream(data, 0, prevLength + (int) dumpInfo.lengthBytes));
                    ais.seek(prevLength);
                    ais.dumpInfo = dumpInfo;
                    new AVM2Code(ais);
                } catch (IOException ex) {
                    Logger.getLogger(DumpTree.class.getName()).log(Level.SEVERE, null, ex);
                }
                repaint();
            }
            break;
            case ACTION_CLOSE_SWF: {
                Main.closeFile(mainPanel.getCurrentSwfList());
            }
            break;
        }
    }

    @Override
    public void setModel(TreeModel tm) {
        super.setModel(tm);

        setUI(new BasicTreeUI() {
            {
                setHashColor(Color.gray);
            }
        });

        if (tm != null) {
            int rowCount = tm.getChildCount(tm.getRoot());
            for (int i = rowCount - 1; i >= 0; i--) {
                expandRow(i);
            }
        }
    }
}
