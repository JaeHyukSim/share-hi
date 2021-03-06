package com.example.android.data.connection;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import com.example.android.data.connection.dto.FileStat;
import com.example.android.data.model.SocketRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;


public class SocketInfo {
    private final String TAG = "SocketInfo";

    public static final String IP = "localhost";
    public static final int PORT = 8003;

    private FileStat fs;

    private SocketRepository socketRepository;
    private Socket socket;
    private String name;
    private int CHUNK_SIZE = 1024;
    private boolean closeSocketByUser;
    private boolean threadRunning;

    BufferedReader in;
    PrintWriter out;
    Gson gson = new Gson();

    public SocketInfo(SocketRepository socketRepository) {
        this.socketRepository = socketRepository;
    }

    public void connect(String name) {
        this.name = name;
        try {
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(IP, PORT);
            socket.connect(socketAddress, 8288);
            Log.i(TAG, "connect: success");
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            threadRunning = true;
            getIO.start();
            socketRepository.successSocketConnection();
            closeSocketByUser = false;
        } catch (Exception e) {
            e.printStackTrace();
            socketRepository.failSocketConnection();
        }
    }

    public void disConnect() {
        //소켓 통신 중지하기
        try {
            closeSocketByUser = true;
            socket.close();
            threadRunning = false;
            socketRepository.successSocketClosed();
            Log.i(TAG, "run: stop socket");
        } catch (IOException e) {
            e.printStackTrace();
            socketRepository.failSocketClosed();
        }
    }

    public void write(String data) {
        //data를 Stream에 입력
        out.println(data);
        out.flush();
    }

    private Thread getIO = new Thread() {
        @Override
        public void run() {
            try {
                while (threadRunning) {
                    String data = in.readLine();

                    if (data == null) {
                        Log.i(TAG, "run: null");
                        throw new IOException();
                    }

                    Log.i(TAG, "run: data - "+data);

                    JSONObject jsonObject = new JSONObject(data);
                    int namespace = jsonObject.getInt("namespace");

                    JsonObject jobj;
                    String json;
                    switch (namespace) {
                        /**
                         * Android
                         * 1010
                         * 응답에 성공했을 때 수행되는 코드 설명 : 서버와 성공적으로 응답한 경우 수행된다
                         * data : {"namespace":1010,"status":200,"message":"OK"}
                         */
                        case 1010:
                            jobj = new JsonObject();
                            jobj.addProperty("namespace", "1020");
                            jobj.addProperty("adId", name);
                            json = gson.toJson(jobj);
                            write(json);
                            break;

                        /**
                         * Android
                         * 1070
                         * 설명 : 현재 공유하고 있는 root path를 전송한다
                         * 메시지 :{"namespace":1010,"path":path value}
                         */
                        case 1070:
                            String rootPath = socketRepository.getRootPath();

                            jobj = new JsonObject();
                            jobj.addProperty("namespace", "1070");
                            jobj.addProperty("targetId", jsonObject.getString("targetId"));
                            jobj.addProperty("path", rootPath);
                            json = gson.toJson(jobj);
                            write(json);
                            break;

                        /**
                         * Android
                         * 2100
                         * 설명 : 폴더 디렉토리 구조를 공유 디바이스에게 요청한다.
                         * 메시지 : {"namespace":2100,"targetId":"9ebe9cf8-61aa-43ee-bcfc-66005e81f287","path":"./"}
                         */
                        case 2100:
                            String returnJSONObjectString = socketRepository.getFolderDirectory(jsonObject.getString("path")).toString();
                            Log.i("myTag", "2100: " + returnJSONObjectString.length());
                            int chunkCount = (returnJSONObjectString.length() / CHUNK_SIZE) + (returnJSONObjectString.length() % CHUNK_SIZE > 0 ? 1 : 0);

                            jobj = new JsonObject();
                            jobj.addProperty("namespace", "2100");
                            jobj.addProperty("targetId", jsonObject.getString("targetId"));
                            jobj.addProperty("path", jsonObject.getString("path"));
                            jobj.addProperty("chunkCount", chunkCount);
                            json = gson.toJson(jobj);
                            write(json);
                            break;

                        case 2150:
                            //chunkCount : 보내야 할 Json 청크 개수
                            // pathDataChunkCount : 지금까지 보낸 Json 청크 개수
                            String path = jsonObject.getString("path");
                            String targetId = jsonObject.getString("targetId");
                            chunkCount = jsonObject.getInt("chunkCount");
                            int num = jsonObject.getInt("pathDataChunkCount");
                            if (num < 0 || num >= chunkCount) {
                                break;
                            }
                            returnJSONObjectString = socketRepository.getFolderDirectory(path).toString();
                            int length = returnJSONObjectString.length();
                            int start = num * CHUNK_SIZE;
                            int next = Math.min(length, CHUNK_SIZE * (num + 1));

                            jobj = new JsonObject();
                            jobj.addProperty("namespace", "2150");
                            jobj.addProperty("targetId", targetId);
                            jobj.addProperty("path", path);
                            jobj.addProperty("chunkCount", chunkCount);
                            jobj.addProperty("pathDataChunkCount", num);
                            jobj.addProperty("data", returnJSONObjectString.substring(start, next));
                            json = gson.toJson(jobj);
                            write(json);
                            break;

                        /**
                         * Android
                         * 2101
                         * 설명 : 폴더(파일) 이름을 수정한다
                         * 메시지 :
                         */
                        case 2101:
                            String newName = jsonObject.getString("newName");
                            path = jsonObject.getString("path");
                            String name = jsonObject.getString("name");
                            boolean result2101 = socketRepository.changeFolderName(path, name, newName);
                            Log.i(TAG, "run: 2101 - "+result2101);
                            break;

                        /**
                         * Android
                         * 2102
                         * 설명 : 폴더(파일)를 삭제한다
                         * 메시지 :
                         */
                        case 2102:
                            boolean result2102 = socketRepository.deleteFolder(jsonObject.getString("path"), jsonObject.getString("name"));
                            Log.i(TAG, "run: 2102 - "+result2102);
                            break;

                        /**
                         * Android
                         * 2103
                         * 설명 : 폴더를 추가한다
                         * 메시지 :
                         */
                        case 2103:
                            boolean result2103 = socketRepository.createFolder(jsonObject.getString("path"), jsonObject.getString("name"));
                            Log.i(TAG, "run: 2103 - "+result2103);

                            break;

                        case 7100: // 파일 스텟 확인
                            int size = jsonObject.getInt("size");
                            String ext = jsonObject.getString("ext");
                            String path7100 = jsonObject.getString("path");
                            String name7100 = jsonObject.getString("name");

                            File file = new File(path7100 + "/" + name7100 + ext);

                            // 파일이 이미 있는지 확인
                            if (file.length() == size) {
                                Log.i(TAG, "run: file is already exist");
                                fs = new FileStat(name7100, path7100, ext, 0, 0);
                                jobj = new JsonObject();
                                jobj.addProperty("namespace", "7004");
                                jobj.addProperty("targetId", jsonObject.getString("targetId"));
                                jobj.addProperty("status", "400"); // or 403 FORBIDDEN
                                jobj.addProperty("message", "BAD REQUEST");
                                jobj.addProperty("detail", "");
                                jobj.addProperty("content", "이미 파일이 있습니다.");
                                json = gson.toJson(jobj);
                                write(json);
                                break;
                            } else {

                                long tmpfileSize = 0;
                                if (file.exists()) {
                                    Log.i(TAG, "run: continue to download file");
                                    tmpfileSize = file.length();
                                    Log.i(TAG, "run: current file size - " + tmpfileSize);
                                }

                                fs = new FileStat(name7100, path7100, ext, size, tmpfileSize);
                                // 새로운 TCP 연결 시도
                                socketRepository.getSocketFile(fs);
                            }
                            break;

                        /**
                         * Android
                         * 7001
                         * 설명 : 파일이 전송된 비율을 받는다
                         * 메시지 :
                         */
                        case 7001:
                            int percentage = jsonObject.getInt("percent");
                            Log.i(TAG, "run: percent - "+ percentage);
                            break;

                        /**
                         * Android
                         * 7200
                         * 설명 : 파일 전송을 위한 TCP 연결이 완료되었다.
                         * 메시지 :
                         */
                        case 7200:
                            Log.i(TAG, "run: file size - "+fs.getSize());
                            if (fs.getSize() == 0) break;
                            jobj = new JsonObject();
                            jobj.addProperty("namespace", "7100");
                            jobj.addProperty("tmpfileSize", fs.getTmpfileSize());
                            jobj.addProperty("size", fs.getSize());
                            json = gson.toJson(jobj);
                            write(json);
                            break;

                        /**
                         * Android
                         * 8100
                         * 설명 : 안드로이드가 업로드할 파일의 정보를 전달한다.
                         * 메시지 :
                         */
                        case 8100:
                            String name2 = jsonObject.getString("name");
                            String path2 = jsonObject.getString("path");
                            String ext2 = jsonObject.getString("ext");
                            File file2 = new File(path2 + "/" + name2 + ext2);
                            fs = new FileStat(name2, path2, ext2, file2.length(), 0);
                            targetId = jsonObject.getString("targetId");

                            jobj = new JsonObject();
                            jobj.addProperty("namespace", "8100");
                            jobj.addProperty("size", file2.length());
                            jobj.addProperty("targetId", targetId);
                            json = gson.toJson(jobj);
                            write(json);
                            break;

                        /**
                         * Android
                         * 8200
                         * 설명 : 안드로이드가 파일 업로드를 시작한다.
                         * 메시지 :
                         */
                        case 8200:
                            SocketUpload socketUpload = new SocketUpload(fs);
                            socketUpload.connect();
                            break;
                    }

                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
                //사용자의 의도에 의한 종료가 아닐 경우
                if (!closeSocketByUser)
                    socketRepository.failSocketConnection();
                Log.i(TAG, "run: stop socket exception");
            }
        }
    };
}
