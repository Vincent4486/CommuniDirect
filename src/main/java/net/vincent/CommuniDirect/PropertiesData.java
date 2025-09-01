package net.vincent.CommuniDirect;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

public class PropertiesData{

    CommuniDirect communiDirect;

    String path = Objects.requireNonNull(System.getProperty("user.dir") + "/communidirect.properties");

    public PropertiesData(CommuniDirect communiDirect){

        this.communiDirect = communiDirect;

    }

    public void load(){

        try(FileInputStream fileInputStream = new FileInputStream(path)){

            Properties properties = new Properties();
            properties.load(fileInputStream);
            CommuniDirect.port_ = Integer.parseInt(properties.getProperty("port"));

        }catch (FileNotFoundException e){
            create();
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    public void save(){

        try(FileOutputStream fileOutputStream = new FileOutputStream(path)){

            Properties properties = new Properties();
            properties.setProperty("port", Integer.toString(CommuniDirect.port_));
            properties.store(fileOutputStream, "properties file");

        }catch (IOException e){
            e.printStackTrace();
        }


    }

    public void create(){

        try(FileOutputStream fileOutputStream = new FileOutputStream(path)){

            Properties properties = new Properties();
            properties.setProperty("port", "2556");
            properties.store(fileOutputStream, "properties file");

        }catch (IOException e){
            e.printStackTrace();
        }

    }

}
