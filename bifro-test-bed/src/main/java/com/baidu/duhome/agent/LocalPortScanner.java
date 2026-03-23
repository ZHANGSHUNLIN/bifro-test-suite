package com.baidu.duhome.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LocalPortScanner {

    private static final Logger log = LoggerFactory.getLogger(LocalPortScanner.class);
    private static final Logger tagLogger = LoggerFactory.getLogger("tagLogger");

    private final int startPort = 1024;
    private final int endPort = 65535;  // 修正：应该是65535，不是65536
    private final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    private final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

    public  Map<String, Queue<Integer>> getAvailablePorts(){
        Map<String, Queue<Integer>> map;
        try {
            List<InetAddress> allLocalIps = getAllLocalIps();
            map = getAvailablePorts(allLocalIps);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    /**
     * 获取指定数量的可用端口
     */
    public Map<String, Queue<Integer>> getAvailablePorts(List<InetAddress> localIps) throws IOException {
        Map<String, Queue<Integer>> availablePorts = new HashMap<>();
        Map<String, Queue<Integer>> usedPorts = scanUsedPorts(localIps);

        for (Map.Entry<String, Queue<Integer>> entry : usedPorts.entrySet()) {
            String ip = entry.getKey();
            Queue<Integer> used = entry.getValue();
            Queue<Integer> available = new ConcurrentLinkedQueue<>();

            // 使用流处理，提高可读性
            IntStream.rangeClosed(startPort, endPort)
                    .filter(port -> !used.contains(port))
                    .forEach(available::add);

            availablePorts.put(ip, available);
        }
        return availablePorts;
    }

    /**
     * 查询已被占用的IP和端口（返回Set提高查找效率）
     */
    public Map<String, Queue<Integer>> scanUsedPorts(List<InetAddress> localIps) throws IOException {
        Map<String, Queue<Integer>> usedPortsMap = new HashMap<>();
        Set<String> targetIps = new HashSet<>();

        for (InetAddress ip : localIps) {
            String ipStr = ip.getHostAddress();
            targetIps.add(ipStr);
            usedPortsMap.put(ipStr, new ConcurrentLinkedQueue<>());
        }

        // 根据不同系统查询已占用端口
        try {
            if (IS_LINUX) {
                readLinuxUsedPorts(usedPortsMap, targetIps);
            } else if (IS_MAC || IS_WINDOWS) {
                readMacOrWindowsUsedPorts(usedPortsMap, targetIps);
            } else {
                throw new UnsupportedOperationException("不支持的操作系统: " + System.getProperty("os.name"));
            }
        } catch (Exception e) {
            log.error("扫描端口失败", e);
            throw new IOException("扫描端口失败", e);
        }

        tagLogger.debug("已占用端口: {}", usedPortsMap);
        return usedPortsMap;
    }

    /**
     * Linux系统：读取/proc/net/tcp文件
     */
    private void readLinuxUsedPorts(Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) throws IOException {
        Path tcpFile = Paths.get("/proc/net/tcp");
        if (!Files.exists(tcpFile)) {
            log.warn("文件不存在: {}", tcpFile);
            return;
        }

        try (BufferedReader br = Files.newBufferedReader(tcpFile)) {
            br.readLine(); // 跳过标题行
            String line;
            while ((line = br.readLine()) != null) {
                processLinuxTcpLine(line.trim(), usedPortsMap, targetIps);
            }
        }
    }

    private void processLinuxTcpLine(String line, Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            return;
        }

        String localAddr = parts[1];
        String[] ipPort = localAddr.split(":");
        if (ipPort.length != 2) {
            return;
        }

        try {
            InetAddress ip = hexToIp(ipPort[0]);
            if (ip == null) return;

            String ipStr = ip.getHostAddress();
            if (targetIps.contains(ipStr)) {
                int port = Integer.parseInt(ipPort[1], 16);
                usedPortsMap.get(ipStr).add(port);
            }
        } catch (Exception e) {
            log.debug("解析失败的行: {}, 错误: {}", line, e.getMessage());
        }
    }

    /**
     * 16进制IP地址转换（小端序）
     */
    private InetAddress hexToIp(String hexIp) {
        if (hexIp == null || hexIp.length() != 8) {
            return null;
        }

        try {
            // 反转16进制字符串（小端序）
            StringBuilder ipBuilder = new StringBuilder();
            for (int i = 6; i >= 0; i -= 2) {
                ipBuilder.append(Integer.parseInt(hexIp.substring(i, i + 2), 16));
                if (i > 0) ipBuilder.append(".");
            }

            return InetAddress.getByName(ipBuilder.toString());
        } catch (Exception e) {
            log.debug("IP转换失败: {}, 错误: {}", hexIp, e.getMessage());
            return null;
        }
    }

    /**
     * macOS/Windows系统：执行netstat命令
     */
    private void readMacOrWindowsUsedPorts(Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) throws IOException {
        Process process = null;
        try {
            String[] command = buildNetstatCommand();
            process = new ProcessBuilder(command).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processNetstatLine(line.trim(), usedPortsMap, targetIps);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("netstat命令执行异常，退出码: {}", exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("进程被中断", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private String[] buildNetstatCommand() {
        if (IS_MAC) {
            return new String[]{"netstat", "-an", "-p", "tcp"};
        } else {
            return new String[]{"netstat", "-ano", "-p", "TCP"};  // Windows增加-o参数显示PID
        }
    }

    private void processNetstatLine(String line, Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) {
        if (line.isEmpty() || line.startsWith("Active") || line.startsWith("Proto")) {
            return;
        }

        String[] parts = line.split("\\s+");
        if (parts.length < 4) {
            return;
        }

        String protocol = parts[0];
        String localAddress = parts[3];
        String state = parts.length > 4 ? parts[4] : "";

        // 只处理TCP连接
        if (!protocol.toLowerCase().startsWith("tcp")) {
            return;
        }

        // 只关注监听和已建立的连接
        if (!isRelevantState(state)) {
            return;
        }

        parseAddress(localAddress, usedPortsMap, targetIps);
    }

    private boolean isRelevantState(String state) {
        String upperState = state.toUpperCase();
        return upperState.contains("LISTEN") || upperState.contains("ESTABLISHED");
    }

    private void parseAddress(String address, Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) {
        if (address.contains("[")) {  // IPv6地址，如[::1]:8080
            parseIpv6Address(address, usedPortsMap, targetIps);
        } else if (address.contains(":")) {  // IPv4地址
            parseIpv4Address(address, usedPortsMap, targetIps);
        }
    }

    private void parseIpv4Address(String address, Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) {
        int lastColon = address.lastIndexOf(':');
        if (lastColon == -1) return;

        String ipPart = address.substring(0, lastColon);
        String portStr = address.substring(lastColon + 1);

        // 处理特殊IP
        if (ipPart.equals("*") || ipPart.equals("0.0.0.0") || ipPart.isEmpty()) {
            addPortToAllIps(portStr, usedPortsMap, targetIps);
        } else {
            addPortToSpecificIp(ipPart, portStr, usedPortsMap, targetIps);
        }
    }

    private void parseIpv6Address(String address, Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) {
        // 处理IPv6地址格式：[::1]:8080
        int bracketEnd = address.indexOf(']');
        if (bracketEnd == -1) return;

        String ipPart = address.substring(1, bracketEnd);
        int colonIndex = address.lastIndexOf(':');
        if (colonIndex <= bracketEnd) return;

        String portStr = address.substring(colonIndex + 1);

        // IPv6本地地址映射到IPv4的127.0.0.1
        if (ipPart.equals("::1") || ipPart.equals("::")) {
            if (targetIps.contains("127.0.0.1")) {
                try {
                    int port = Integer.parseInt(portStr);
                    usedPortsMap.get("127.0.0.1").add(port);
                } catch (NumberFormatException e) {
                    log.debug("端口格式错误: {}", portStr);
                }
            }
        }
    }

    private void addPortToAllIps(String portStr, Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) {
        try {
            int port = Integer.parseInt(portStr);
            for (String ip : targetIps) {
                usedPortsMap.get(ip).add(port);
            }
        } catch (NumberFormatException e) {
            log.debug("端口格式错误: {}", portStr);
        }
    }

    private void addPortToSpecificIp(String ipPart, String portStr, Map<String, Queue<Integer>> usedPortsMap, Set<String> targetIps) {
        // 清理可能的接口标识符
        if (ipPart.contains("%")) {
            ipPart = ipPart.split("%")[0];
        }

        // 验证IP格式
        if (!isValidIpAddress(ipPart)) {
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            if (targetIps.contains(ipPart)) {
                usedPortsMap.get(ipPart).add(port);
            }
        } catch (NumberFormatException e) {
            log.debug("端口格式错误: {}", portStr);
        }
    }

    private boolean isValidIpAddress(String ip) {
        return ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    }

    /**
     * 获取所有本地网络接口的IP地址
     */
    public List<InetAddress> getAllLocalIps() throws SocketException {
        List<InetAddress> allIps = new ArrayList<>();
        allIps.add(InetAddress.getLoopbackAddress());  // 127.0.0.1

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isUp() && !iface.isLoopback()) {
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {  // 只取IPv4
                        allIps.add(addr);
                    }
                }
            }
        }
        return allIps;
    }

    /**
     * 检查单个端口是否可用
     */
    public boolean isPortAvailable(InetAddress address, int port) throws IOException {
        Map<String, Queue<Integer>> usedPorts = scanUsedPorts(Collections.singletonList(address));
        return !usedPorts.get(address.getHostAddress()).contains(port);
    }

    /**
     * 获取下一个可用端口
     */
    public int getNextAvailablePort(InetAddress address) throws IOException {
        Queue<Integer> usedPorts = scanUsedPorts(Collections.singletonList(address))
                .get(address.getHostAddress());

        for (int port = startPort; port <= endPort; port++) {
            if (!usedPorts.contains(port)) {
                return port;
            }
        }
        throw new IOException("没有可用端口");
    }

    /**
     * 测试主函数
     */
    public static void main(String[] args) {
        try {
            LocalPortScanner scanner = new LocalPortScanner();

            // 获取所有本地IP
            List<InetAddress> allIps = scanner.getAllLocalIps();
            System.out.println("本地IP地址列表:");
            allIps.forEach(ip -> System.out.println("  " + ip.getHostAddress()));

            // 扫描已用端口
            Map<String, Queue<Integer>> usedPorts = scanner.scanUsedPorts(allIps);

            System.out.println("\n已占用端口统计:");
            System.out.println("==================");

            for (Map.Entry<String, Queue<Integer>> entry : usedPorts.entrySet()) {
                String ip = entry.getKey();
                Queue<Integer> ports = entry.getValue();

                if (!ports.isEmpty()) {
                    List<Integer> sortedPorts = new ArrayList<>(ports);
                    Collections.sort(sortedPorts);

                    System.out.printf("IP: %s%n", ip);
                    System.out.printf("  占用端口数: %d%n", ports.size());
                    System.out.printf("  端口列表: %s%n",
                            sortedPorts.stream()
                                    .limit(20)  // 只显示前20个
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", ")));
                    if (ports.size() > 20) {
                        System.out.printf("  ... 还有%d个端口未显示%n", ports.size() - 20);
                    }
                    System.out.println();
                }
            }

            // 获取可用端口示例
            System.out.println("\n获取5个可用端口:");
            InetAddress testIp = InetAddress.getByName("127.0.0.1");
            Map<String, Queue<Integer>> availableMap = scanner.getAvailablePorts(
                    Collections.singletonList(testIp));

            availableMap.forEach((ip, ports) ->
                    System.out.printf("IP %s 的可用端口: %s%n", ip, ports));

            // 检查特定端口
            int testPort = 8080;
            boolean available = scanner.isPortAvailable(testIp, testPort);
            System.out.printf("\n端口 %d 是否可用: %s%n", testPort, available ? "是" : "否");

        } catch (Exception e) {
            log.error("端口扫描出错", e);
        }
    }
}