package com.lorne.tx.compensate.repository;

import com.google.common.collect.Lists;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.exception.TransactionRuntimeException;
import com.lorne.tx.serializer.KryoSerializer;
import com.lorne.tx.serializer.ObjectSerializer;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.List;

/**
 * <p>Description: .</p>
 * <p>Company: 深圳市旺生活互联网科技有限公司</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 * 文件的实现方式
 *
 * @author yu.xiao@happylifeplat.com
 * @version 1.0
 * @date 2017/5/11 14:33
 * @since JDK 1.8
 */
@Component
public class FileTransactionRecoverRepository implements TransactionRecoverRepository {


   // private static final String rootPath = "/tx";

    private String tableName;

    private String filePath;

    private volatile static boolean initialized;

    private static ObjectSerializer serializer;

    /**
     * 创建本地事务对象
     *
     * @param transactionRecover 事务对象
     * @return rows
     */
    @Override
    public int create(TransactionRecover transactionRecover) {
        writeFile(transactionRecover);
        return 1;
    }

    /**
     * 删除对象
     *
     * @param id 事务对象id
     * @return rows
     */
    @Override
    public int remove(String id) {
        String fullFileName = getFullFileName(id);
        File file = new File(fullFileName);
        if (file.exists()) {
            file.delete();
        }
        return 1;
    }

    /**
     * 更改事务对象
     *
     * @param id           事务对象id
     * @param lastTime
     * @param retriedCount 执行次数  @return rows
     */
    @Override
    public int update(String id, Date lastTime, int retriedCount) {

        String fullFileName = getFullFileName(id);
        File file = new File(fullFileName);
        if (file.exists()) {
            TransactionRecover transactionRecover = readTransaction(file);
            transactionRecover.setLastTime(lastTime);
            transactionRecover.setRetriedCount(retriedCount);
            transactionRecover.setVersion(1);
            writeFile(transactionRecover);
        }
        return 1;
    }

    /**
     * 获取需要提交的事务
     *
     * @return List<TransactionRecover>
     */
    @Override
    public List<TransactionRecover> findAll() {

        List<TransactionRecover> transactionRecoverList = Lists.newArrayList();
        File path = new File(filePath);
        File[] files = path.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                TransactionRecover transaction = readTransaction(file);
                transactionRecoverList.add(transaction);

                transaction.setVersion(transaction.getVersion()+1);
                writeFile(transaction);

            }

        }

        return transactionRecoverList;
    }

    /**
     * 创建表等操作
     *
     * @param modelName
     */
    @Override
    public void init(String modelName) {
        serializer = new KryoSerializer();
        String configPath = ConfigUtils.getString("tx.properties", "compensate.file.path");
        this.tableName = "lcn_tx_"+modelName.replaceAll("-","_");
        filePath =configPath+"/"+tableName;
        File file = new File(filePath);
        if(!file.exists()){
            file.getParentFile().mkdirs();
            file.mkdirs();
        }
    }

    private void writeFile(TransactionRecover transaction) {
        makeDirIfNecessory();

        String file = getFullFileName(transaction.getId());

        FileChannel channel = null;
        RandomAccessFile raf;
        try {
            byte[] content = serialize(transaction);
            raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(content.length);
            buffer.put(content);
            buffer.flip();

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }

            channel.force(true);
        } catch (Exception e) {
            throw new TransactionRuntimeException(e);
        } finally {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException e) {
                    throw new TransactionRuntimeException(e);
                }
            }
        }
    }

    private String getFullFileName(String id) {
        return String.format("%s/%s", filePath, id);
    }

    private TransactionRecover readTransaction(File file) {

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);

            byte[] content = new byte[(int) file.length()];

            fis.read(content);

            if (content != null) {
                return deserialize(content);
            }
        } catch (Exception e) {
            throw new TransactionRuntimeException(e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    throw new TransactionRuntimeException(e);
                }
            }
        }

        return null;
    }

    private void makeDirIfNecessory() {
        if (!initialized) {
            synchronized (FileTransactionRecoverRepository.class) {
                if (!initialized) {
                    File rootPathFile = new File(filePath);
                    if (!rootPathFile.exists()) {

                        boolean result = rootPathFile.mkdir();

                        if (!result) {
                            throw new TransactionRuntimeException("cannot create root path, the path to create is:" + filePath);
                        }

                        initialized = true;
                    } else if (!rootPathFile.isDirectory()) {
                        throw new TransactionRuntimeException("rootPath is not directory");
                    }
                }
            }
        }
    }

    public static byte[] serialize(TransactionRecover transaction) throws Exception {
      /*  Map<String, Object> map = Maps.newHashMap();
        map.put("id", transaction.getId());
        map.put("retried_count", transaction.getRetriedCount());
        map.put("create_time", transaction.getCreateTime());
        map.put("last_time", transaction.getLastTime());
        map.put("version", transaction.getVersion());
        map.put("group_id", transaction.getGroupId());
        map.put("task_id", transaction.getTaskId());
        map.put("invocation", serializer.serialize(transaction.getInvocation()));*/

        return serializer.serialize(transaction);

    }

    public static TransactionRecover deserialize(byte[] value) throws Exception {

        return serializer.deSerialize(value, TransactionRecover.class);

    }
}
