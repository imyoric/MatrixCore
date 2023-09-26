package ru.yoricya.minecraft.matrixcore;

import com.google.common.base.Throwables;
import javafx.concurrent.Task;
import org.bukkit.Bukkit;
import org.bukkit.Warning;
import org.bukkit.command.Command;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.SpigotCommand;
import org.spigotmc.SpigotConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

public class MatrixCore {
    public static MatrixAsyncScheduler MatrixAsyncScheduler;
    public MatrixCore(){
        MatrixConfig.init(new File("matrix.yml"));
        MatrixAsyncScheduler = new MatrixAsyncScheduler();
        MatrixAsyncScheduler.TaskShelderInit();
        MatrixAsyncScheduler.TaskShelderRezInit();
    }

    public static void StopMatrix(){
        MatrixAsyncScheduler.StopMatrix();
    }
    
    public static class MatrixAsyncTask{
        Runnable Task;
        boolean isRunned = false;
        Exception exception = null;
        MatrixAsyncTask(Runnable t){
            Task = t;
        }
        void run(){
            try{
                Task.run();
                isRunned = true;
            }catch (Exception e) {
                exception = e;
                e.printStackTrace();
            }
        }

        public void RunnedCheck(){
            if(!Thread.currentThread().getName().equals("Server thread"))
                while(!isRunned){
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        }
        @Warning //Опасно
        public void RunnedCheck(boolean skipServerThreadCheck){
            if(skipServerThreadCheck)
                while(!isRunned){
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        }
    }
    public class MatrixAsyncScheduler{
        //Standart Threads
        public static int CountThreads = MatrixConfig.getInt("basic.count_async_threads", 4);
        public static long[] MatrixTicks = new long[CountThreads];
        List<MatrixAsyncTask>[] TasksThreads = new ArrayList[CountThreads]; //Count Threads

        //Rez Threads
        private static int CountRezThreads = 1;
        public static long[] MatrixRezTicks = new long[CountRezThreads];
        List<MatrixAsyncTask>[] TasksRezThreads = new ArrayList[CountRezThreads]; //Count Threads
        int thr = 0;
        int thrRez = 0;
        int OverLoadedCount = 0;

        public long posOverloadWarn = 0;
        public Boolean SchedulerIsInit = false;

        public MatrixAsyncTask addTask(Runnable task){
            return addTask(task, -1);
        }

        public MatrixAsyncTask addTask(Runnable task, long ms){
            MatrixAsyncTask mat = new MatrixAsyncTask(task);
            if(!SchedulerIsInit) return mat;

            if(TasksThreads[thr] == null) {
                TaskShelderInit(thr, mat);
            }else{
                int minmsec = 140;
                if(ms != -1) {
                    minmsec = (int) ms;
                }
                int countWhiles = 0;
                while((System.currentTimeMillis() - MatrixTicks[thr]) > minmsec){
                    if(countWhiles > CountThreads){
                        Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                            @Override
                            public void run() {
                                task.run();
                            }
                        });
                        int finalMinmsec = minmsec;
                        addRezTask(new Runnable() {
                            @Override
                            public void run() {
                                int msecCheck = 70;
                                if(ms != -1) msecCheck = ((int) ms) / CountThreads;
                                if(finalMinmsec > msecCheck*CountThreads && (System.currentTimeMillis() - posOverloadWarn > 320000 || posOverloadWarn == 0)){
                                    OverLoadedCount++;
                                    Bukkit.getLogger().info("Matrix Threads Full Loaded! Server overloaded?");
                                    posOverloadWarn = System.currentTimeMillis();
                                    if(OverLoadedCount > 24){
                                        for(List<MatrixAsyncTask> r : TasksThreads){
                                            r.clear();
                                        }
                                        OverLoadedCount = 0;
                                        Bukkit.getLogger().info("Matrix Threads Tasks Cleared! Server overload optimizing.");
                                    }
                                }
                            }
                        });
                        return mat;
                    }
                    thr++;
                    countWhiles++;
                    minmsec+=65;
                    if(thr >= CountThreads) thr = 0;
                }
                TasksThreads[thr].add(mat);
            }
            thr+=1;
            if(thr >= CountThreads) thr = 0;
            return mat;
        }

        public void StopMatrix(){
            SchedulerIsInit = false;
            for(List<MatrixAsyncTask> r : TasksThreads){
                if(TasksThreads != null) if(r != null) r.clear();
            }
            for(List<MatrixAsyncTask> r : TasksRezThreads){
                if(TasksRezThreads != null) if(r != null) r.clear();
            }
        }

        public MatrixAsyncTask addRezTask(Runnable task){
            MatrixAsyncTask mat = new MatrixAsyncTask(task);
            TasksRezThreads[thrRez].add(mat);
            thrRez+=1;
            if(thrRez >= CountRezThreads) thrRez = 0;
            return mat;
        }

        public void TaskShelderRezInit(){
            SchedulerIsInit = true;
            for(int current = 0; current < TasksRezThreads.length; current++){
                TasksRezThreads[current] = new ArrayList<>();
                int finalCurrent = current;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MatrixConfig.saveConfing(true);
                        while(SchedulerIsInit){
                            MatrixRezTicks[finalCurrent] = System.currentTimeMillis();
                            try {
                                for(int curTask = 0; curTask < TasksRezThreads[finalCurrent].size(); curTask++){
                                    MatrixAsyncTask r = TasksRezThreads[finalCurrent].get(curTask);
                                    if(r == null) continue;
                                    TasksRezThreads[finalCurrent].remove(r);
                                    try {
                                        r.run();
                                    }catch (ArrayIndexOutOfBoundsException e){e.printStackTrace();}
                                }
                            }catch (ConcurrentModificationException ignore){}
                            try {
                                Thread.sleep(256);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }, "MatrixRezAsyncScheduler_"+current).start();
            }
        }

        public void TaskShelderInit(){
            SchedulerIsInit = true;
            for(int current = 0; current < TasksThreads.length; current++){
                TasksThreads[current] = new ArrayList<>();
                int finalCurrent = current;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while(SchedulerIsInit){
                            MatrixTicks[finalCurrent] = System.currentTimeMillis();
                            try {
                                for(int curTask = 0; curTask < TasksThreads[finalCurrent].size(); curTask++){
                                    MatrixAsyncTask r = TasksThreads[finalCurrent].get(curTask-1);
                                    if(r == null) continue;
                                    TasksThreads[finalCurrent].remove(r);
                                    try {
                                        r.run();
                                    }catch (Exception e){e.printStackTrace();}
                                }
                            }catch (ConcurrentModificationException ignore){}
                            try {
                                Thread.sleep(8);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if(TasksThreads[finalCurrent] == null) return;
                            else if(TasksThreads[finalCurrent].isEmpty()) {
                                MatrixTicks[finalCurrent] = -1;
                                TasksThreads[finalCurrent] = null;
                                return;
                            }
                        }
                    }
                }, "MatrixAsyncScheduler_"+current).start();
            }
        }

        public void TaskShelderInit(int thread, MatrixAsyncTask task){
            if(TasksThreads[thread] != null) return;
            TasksThreads[thread] = new ArrayList<>();
            TasksThreads[thread].add(task);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while(SchedulerIsInit){
                        MatrixTicks[thread] = System.currentTimeMillis();
                        try {
                            for(int curTask = 0; curTask < TasksThreads[thread].size(); curTask++){
                                MatrixAsyncTask r = TasksThreads[thread].get(curTask);
                                if(r == null) continue;
                                TasksThreads[thread].remove(r);
                                try {
                                    r.run();
                                }catch (Exception e){e.printStackTrace();}
                            }
                        }catch (ConcurrentModificationException ignore){}
                        try {
                            Thread.sleep(8);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if(TasksThreads[thread] == null) return;
                        else if(TasksThreads[thread].isEmpty()) {
                            TasksThreads[thread] = null;
                            MatrixTicks[thread] = -1;
                            return;
                        }
                    }
                }
            }, "MatrixAsyncScheduler_"+thread).start();
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
