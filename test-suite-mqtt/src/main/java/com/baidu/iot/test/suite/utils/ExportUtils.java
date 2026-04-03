
package com.baidu.iot.test.suite.utils;

import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.github.abel533.echarts.DataZoom;
import com.github.abel533.echarts.Grid;
import com.github.abel533.echarts.VisualMap;
import com.github.abel533.echarts.axis.CategoryAxis;
import com.github.abel533.echarts.axis.SplitArea;
import com.github.abel533.echarts.axis.ValueAxis;
import com.github.abel533.echarts.code.*;
import com.github.abel533.echarts.json.GsonOption;
import com.github.abel533.echarts.series.Heatmap;
import com.github.abel533.echarts.series.Line;
import com.github.abel533.echarts.style.ItemStyle;
import com.github.abel533.echarts.style.itemstyle.Emphasis;
import com.google.common.collect.Lists;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ExportUtils {

    public static void exportPeriodResults(Queue<StatsBasicResult[]> periodResults, ClassLoader classLoader) {
        exportPeriodResults(periodResults, classLoader, "result.html");
    }

    public static void exportPeriodResults(Queue<StatsBasicResult[]> periodResults,
                                           ClassLoader classLoader, String exportName) {
        if (periodResults.isEmpty()) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String[] xAxisData = new String[periodResults.size()];
        Double[] heatMapYAxisData = Arrays.stream(periodResults.peek()[0].getBucketCounts())
                .map(doubles -> doubles[0]).collect(Collectors.toList()).toArray(new Double[0]);
        double pubMax = -1;
        double subMax = -1;
        Object[][] pubHeatmapData = new Object[periodResults.size() * heatMapYAxisData.length][3];
        Object[][] subHeatmapData = new Object[periodResults.size() * heatMapYAxisData.length][3];
        List<StatsBasicResult[]> resultsCopy = new ArrayList<>(periodResults);
        for (int i = 0; i < resultsCopy.size(); i++) {
            xAxisData[i] = sdf.format(new Date(resultsCopy.get(i)[0].getTimestamp()));
            for (int j = 0; j < heatMapYAxisData.length; j++) {
                Object[] pubNode = new Object[3];
                Object[] subNode = new Object[3];
                pubNode[0] = i;
                subNode[0] = i;
                pubNode[1] = j;
                subNode[1] = j;
                pubNode[2] = resultsCopy.get(i)[0].getBucketCounts()[j][1] == 0.0 ?
                        "-" : resultsCopy.get(i)[0].getBucketCounts()[j][1];
                subNode[2] = resultsCopy.get(i)[1].getBucketCounts()[j][1] == 0.0 ?
                        "-" : resultsCopy.get(i)[1].getBucketCounts()[j][1];
                pubHeatmapData[i * heatMapYAxisData.length + j] = pubNode;
                subHeatmapData[i * heatMapYAxisData.length + j] = subNode;
                pubMax = Math.max(pubMax, resultsCopy.get(i)[0].getBucketCounts()[j][1]);
                subMax = Math.max(subMax, resultsCopy.get(i)[1].getBucketCounts()[j][1]);
            }
        }
        try {
            Configuration configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
            configuration.setClassLoaderForTemplateLoading(classLoader, "");
            configuration.setDefaultEncoding("utf-8");
            Template template = configuration.getTemplate("results.ftl");
            Map modelMap = new HashMap();
            // build echarts json
            // latency heatmap
            GsonOption heatmap = new GsonOption();
            heatmap.tooltip().position(Position.top);
            heatmap.grid(new Grid().height("50%").top("10%"));
            heatmap.xAxis(new CategoryAxis().data(xAxisData).splitArea(new SplitArea().show(true)));
            heatmap.yAxis(new CategoryAxis().data(heatMapYAxisData).splitArea(new SplitArea().show(true)));
            heatmap.visualMap(Lists.newArrayList(new VisualMap().min(0).max((int) pubMax).calculable(true)
                    .orient(Orient.horizontal).left(X.center).bottom("15%")));
            heatmap.title("Pub Latency Heatmap");
            heatmap.series(
                    new Heatmap().name("PUB Latency").symbol(Symbol.none).data(pubHeatmapData).itemStyle(
                            new ItemStyle().emphasis(new Emphasis().shadowBlur(10)
                                    .shadowColor("'rgba(0, 0, 0, 0.5)'").show(true)))
            );
            modelMap.put("pubHeatmap", heatmap.toString());

            heatmap.title("Sub Latency Heatmap");
            heatmap.visualMap().clear();
            heatmap.visualMap(Lists.newArrayList(new VisualMap().min(0).max((int) subMax).calculable(true)
                    .orient(Orient.horizontal).left(X.center).bottom("15%")));
            heatmap.series().clear();
            heatmap.series(
                    new Heatmap().name("SUB Latency").symbol(Symbol.none).data(subHeatmapData).itemStyle(
                            new ItemStyle().emphasis(new Emphasis().shadowBlur(10)
                                    .shadowColor("'rgba(0, 0, 0, 0.5)'").show(true)))
            );
            modelMap.put("subHeatmap", heatmap.toString());

            // latency lines
            GsonOption option = new GsonOption();
            option.tooltip().trigger(Trigger.axis);
            option.legend("PUB", "SUB");
            option.toolbox().show(true);
            option.calculable(true);
            option.xAxis(new CategoryAxis().boundaryGap(false).data(xAxisData));
            option.yAxis(new ValueAxis());
            option.title("Mean Latency in Ms");
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getMeanLatency()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getMeanLatency()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("meanLatency", option.toString());

            option.title("Standard Deviation in Ms");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getStandardDeviation()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getStandardDeviation()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("standardDeviation", option.toString());

            option.title("Median Latency in Ms");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getMedianLatency()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getMedianLatency()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("medianLatency", option.toString());

            option.title("p95Latency in Ms");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getP95Latency()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getP95Latency()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("p95Latency", option.toString());

            option.title("p99Latency in Ms");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getP99Latency()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getP99Latency()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("p99Latency", option.toString());

            option.title("p999Latency in Ms");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getP999Latency()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getP999Latency()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("p999Latency", option.toString());


            option.title("Max Latency in Ms");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getMaxLatency()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getMaxLatency()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("maxLatency", option.toString());

            option.title("Min Latency in Ms");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getMinLatency()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getMinLatency()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("minLatency", option.toString());

            option.title("qps");
            option.series().clear();
            option.series(
                    new Line().smooth(true).name("PUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[0].getQps()).toArray()),
                    new Line().smooth(true).name("SUB").symbol(Symbol.none)
                            .data(resultsCopy.stream().map(results -> results[1].getQps()).toArray())
            );
            option.dataZoom(setDataZoom());
            modelMap.put("qps", option.toString());
            try (Writer out = new FileWriter(exportName + ".html")) {
                template.process(modelMap, out);
            }catch (Exception exception) {
                log.error("exception for export: ", exception);
            }
        } catch (Exception e) {
            log.error("Failed to export period results", e);
        }
    }

    private static DataZoom setDataZoom() {
        DataZoom dataZoom = new DataZoom();
        dataZoom.setStart(0);
        dataZoom.setEnd(100);
        return dataZoom;
    }
}
