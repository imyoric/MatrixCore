package ru.yoricya.minecraft.matrixcore;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.*;
import java.util.concurrent.*;

public class MatrixCore {
    public static MatrixAsyncScheduler MatrixAsyncScheduler;
    public MatrixCore(){
        MatrixConfig.init(new File("matrix.yml"));
        MatrixAsyncScheduler = new MatrixAsyncScheduler();
    }

    public static void StopMatrix(){
        MatrixAsyncScheduler.StopMatrix();
        MatrixConfig.saveConfing();
    }
    
    public static class MatrixAsyncTask{
        Runnable Task;
        boolean isRunned = false;
        Thread runnedThread;
        Exception exception = null;
        Future<Integer> future;

        MatrixAsyncTask(Runnable t, ForkJoinPool ex) {
            Task = t;
            ex.execute(new Runnable() {
                @Override
                public void run() {
                    runnedThread = Thread.currentThread();
                    try {
                        t.run();
                    }catch (Exception e){
                        exception = e;
                        e.printStackTrace();
                    }
                    isRunned = true;
                }
            });
        }

        public void RunnedCheck(){
            try {
                if(!Thread.currentThread().getName().equals("Server thread")) {
                   //while(future == null) Thread.sleep(8);
                    while (!isRunned) Thread.sleep(8);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        public void RunnedCheck(boolean skipServerThreadCheck){
            if(skipServerThreadCheck)
                try {
                    //while(future == null) Thread.sleep(8);
                    while (!isRunned) Thread.sleep(8);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            else RunnedCheck();
        }
    }
    public class MatrixAsyncScheduler{
        //Standart Threads
        public static int CountThreads = MatrixConfig.getInt("basic.executor_count_max_used_threads", 8);
        public static long[] MatrixTicks = new long[0];

        protected ForkJoinPool Executor;
        protected ForkJoinPool LongestExecutor = new ForkJoinPool();

        public MatrixAsyncScheduler(){
            if(CountThreads == -1)
                Executor = new ForkJoinPool();
            else Executor = new ForkJoinPool(CountThreads);
        }

        public MatrixAsyncTask addTask(Runnable task){
            return addTask(task, -1);
        }

        public MatrixAsyncTask addTask(Runnable task, long ms){
            if(ms < 1000)
                return new MatrixAsyncTask(task, Executor);
            else
                return new MatrixAsyncTask(task, LongestExecutor);
        }

        public void StopMatrix() {
            Executor.shutdown();
        }
    }
    public static class MatrixConfig{
        static YamlConfiguration config;
        static File CONFIG_FILE;
        private static final String HEADER = "MatrixCore Configurating File\n";
        static void init(File configFile){
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            CONFIG_FILE = configFile;
            config = new YamlConfiguration();
            try
            {
                config.load( CONFIG_FILE );
            }catch (FileNotFoundException e){
                try {
                    if(!configFile.createNewFile()) System.out.println("Could not load "+configFile.getName()+" while system error, available filesystem permission?");
                } catch (IOException ex) {
                    System.out.println("Could not load "+configFile.getName()+" while system error");
                    throw new RuntimeException(e);
                }
            }catch (Exception ex ){
                System.out.println("Could not load "+configFile.getName()+", please correct your syntax errors");
                throw new RuntimeException(ex);
            }

            config.options().header( HEADER );
            config.options().copyDefaults(true);
        }
        public static void saveConfing(){
            posSystemSavetime = System.currentTimeMillis();
            try {
                config.save(CONFIG_FILE);
            } catch (IOException e) {
                System.out.println("Could not save "+CONFIG_FILE.getName()+", please correct your syntax errors");
                throw new RuntimeException(e);
            }
        }
        static long posSystemSavetime = 0;
        static void saveConfing(boolean ifRezThread){
            if(ifRezThread && System.currentTimeMillis() - posSystemSavetime > 5000){
                posSystemSavetime = System.currentTimeMillis();
                saveConfing();
            }else saveConfing();

        }

        private static boolean getBoolean(String path, boolean def)
        {
            config.addDefault( path, def );
            return config.getBoolean( path, config.getBoolean( path ) );
        }

        private static int getInt(String path, int def)
        {
            config.addDefault( path, def );
            return config.getInt( path, config.getInt( path ) );
        }

        private static <T> List getList(String path, T def)
        {
            config.addDefault( path, def );
            return (List<T>) config.getList( path, config.getList( path ) );
        }

        private static String getString(String path, String def)
        {
            config.addDefault( path, def );
            return config.getString( path, config.getString( path ) );
        }

        private static double getDouble(String path, double def)
        {
            config.addDefault( path, def );
            return config.getDouble( path, config.getDouble( path ) );
        }
    }
}
