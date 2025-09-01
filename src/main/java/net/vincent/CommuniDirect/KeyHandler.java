package net.vincent.CommuniDirect;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements ActionListener {

    CommuniDirect communiDirect;

    public KeyHandler(CommuniDirect communiDirect){

        this.communiDirect = communiDirect;

    }

    @Override
    public void actionPerformed(ActionEvent e) {

        Command src = (Command) e.getSource();
        src.execCommand(src.getText());

    }

}
