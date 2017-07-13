package com.lorne.tx.compensate.repository;

import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.exception.TransactionException;
import com.lorne.tx.serializer.JavaSerializer;
import com.lorne.tx.serializer.ObjectSerializer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 * jdbc实现
 *
 * @author yu.xiao@happylifeplat.com
 * @version 1.0
 * @date 2017/7/12 10:36
 * @since JDK 1.8
 */
@Component
public class FileTransactionRecoverRepository implements TransactionRecoverRepository {


    private Logger logger = LoggerFactory.getLogger(FileTransactionRecoverRepository.class);

    private String tableName;

    private String filePath;

    private ObjectSerializer serializer;


    public FileTransactionRecoverRepository() {
        serializer = new JavaSerializer();
    }

    @Override
    public int create(TransactionRecover recover) {
        File file = new File(filePath+"/"+recover.getId());
        try {
            byte[] bs = serializer.serialize(recover);
            StreamUtils.copy(bs,new FileOutputStream(file));
            return 1;
        } catch (Exception e) {
            logger.error("create->"+e.getMessage());
        }
        return 0;
    }

    @Override
    public int remove(String id) {
        File file = new File(filePath+"/"+id);
        if(file.exists()){
            return file.delete()?1:0;
        }
        return 0;
    }

    @Override
    public int update(String id, Date lastTime, int retriedCount) {
        File file = new File(filePath+"/"+id);
        try {
            byte bs[] =  IOUtils.toByteArray(new FileInputStream(file));
            TransactionRecover recover = serializer.deSerialize(bs,TransactionRecover.class);
            recover.setLastTime(lastTime);
            recover.setRetriedCount(retriedCount);
            recover.setVersion(1);
            bs = serializer.serialize(recover);
            StreamUtils.copy(bs,new FileOutputStream(file));
            return 1;
        } catch (Exception e) {
            logger.error("update->"+e.getMessage());
        }
        return 0;
    }

    @Override
    public List<TransactionRecover> findAll() {
        File file = new File(filePath+"/");
        List<TransactionRecover> list = new ArrayList<>();
        try {
            for(String f:file.list()){
                File path = new File(filePath+"/"+f);
                byte bs[] =  IOUtils.toByteArray(new FileInputStream(path));
                TransactionRecover recover = serializer.deSerialize(bs,TransactionRecover.class);
                if(recover.getVersion()==1){
                    recover.setVersion(recover.getVersion()+1);
                    bs = serializer.serialize(recover);
                    StreamUtils.copy(bs,new FileOutputStream(file));
                    list.add(recover);
                }
            }
        } catch (Exception e) {
            logger.error("create->"+e.getMessage());
        }
        return list;
    }




    @Override
    public void init(String modelName) {
        String configPath = ConfigUtils.getString("tx.properties", "compensate.file.path");
        this.tableName = "lcn_tx_"+modelName.replaceAll("-","_");
        filePath =configPath+"/"+tableName;
        File file = new File(filePath);
        if(!file.exists()){
            file.getParentFile().mkdirs();
            file.mkdirs();
        }
    }

}
