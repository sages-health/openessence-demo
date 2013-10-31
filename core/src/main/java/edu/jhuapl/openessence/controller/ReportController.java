/*
 * Copyright (c) 2013 The Johns Hopkins University/Applied Physics Laboratory
 *                             All rights reserved.
 *
 * This material may be used, modified, or reproduced by or for the U.S.
 * Government pursuant to the rights granted under the clauses at
 * DFARS 252.227-7013/7014 or FAR 52.227-14.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * NO WARRANTY.   THIS MATERIAL IS PROVIDED "AS IS."  JHU/APL DISCLAIMS ALL
 * WARRANTIES IN THE MATERIAL, WHETHER EXPRESS OR IMPLIED, INCLUDING (BUT NOT
 * LIMITED TO) ANY AND ALL IMPLIED WARRANTIES OF PERFORMANCE,
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT OF
 * INTELLECTUAL PROPERTY RIGHTS. ANY USER OF THE MATERIAL ASSUMES THE ENTIRE
 * RISK AND LIABILITY FOR USING THE MATERIAL.  IN NO EVENT SHALL JHU/APL BE
 * LIABLE TO ANY USER OF THE MATERIAL FOR ANY ACTUAL, INDIRECT,
 * CONSEQUENTIAL, SPECIAL OR OTHER DAMAGES ARISING FROM THE USE OF, OR
 * INABILITY TO USE, THE MATERIAL, INCLUDING, BUT NOT LIMITED TO, ANY DAMAGES
 * FOR LOST PROFITS.
 */

package edu.jhuapl.openessence.controller;

import edu.jhuapl.bsp.detector.TemporalDetectorSimpleDataObject;
import edu.jhuapl.bsp.detector.exception.DetectorException;
import edu.jhuapl.bsp.detector.temporal.epa.NoDetectorDetector;
import edu.jhuapl.graphs.Encoding;
import edu.jhuapl.graphs.GraphException;
import edu.jhuapl.graphs.GraphSource;
import edu.jhuapl.graphs.PointInterface;
import edu.jhuapl.graphs.controller.DefaultGraphData;
import edu.jhuapl.graphs.controller.GraphController;
import edu.jhuapl.graphs.controller.GraphDataHandlerInterface;
import edu.jhuapl.graphs.controller.GraphDataInterface;
import edu.jhuapl.graphs.controller.GraphDataSerializeToDiskHandler;
import edu.jhuapl.graphs.controller.GraphObject;
import edu.jhuapl.openessence.datasource.Dimension;
import edu.jhuapl.openessence.datasource.FieldType;
import edu.jhuapl.openessence.datasource.Filter;
import edu.jhuapl.openessence.datasource.OeDataSource;
import edu.jhuapl.openessence.datasource.OeDataSourceAccessException;
import edu.jhuapl.openessence.datasource.OeDataSourceException;
import edu.jhuapl.openessence.datasource.Record;
import edu.jhuapl.openessence.datasource.dataseries.AccumPoint;
import edu.jhuapl.openessence.datasource.dataseries.DataSeriesSource;
import edu.jhuapl.openessence.datasource.dataseries.Grouping;
import edu.jhuapl.openessence.datasource.dataseries.GroupingDimension;
import edu.jhuapl.openessence.datasource.jdbc.JdbcOeDataSource;
import edu.jhuapl.openessence.datasource.jdbc.QueryRecord;
import edu.jhuapl.openessence.datasource.jdbc.ResolutionHandler;
import edu.jhuapl.openessence.datasource.jdbc.dataseries.AccumPointImpl;
import edu.jhuapl.openessence.datasource.jdbc.dataseries.GroupingImpl;
import edu.jhuapl.openessence.datasource.jdbc.entry.JdbcOeDataEntrySource;
import edu.jhuapl.openessence.datasource.jdbc.filter.FieldFilter;
import edu.jhuapl.openessence.datasource.jdbc.filter.GteqFilter;
import edu.jhuapl.openessence.datasource.jdbc.filter.LteqFilter;
import edu.jhuapl.openessence.datasource.jdbc.filter.sorting.OrderByFilter;
import edu.jhuapl.openessence.datasource.jdbc.timeresolution.sql.pgsql.PgSqlDateHelper;
import edu.jhuapl.openessence.datasource.jdbc.timeresolution.sql.pgsql.PgSqlWeeklyHandler;
import edu.jhuapl.openessence.datasource.ui.ChildTableConfiguration;
import edu.jhuapl.openessence.datasource.ui.DimensionConfiguration;
import edu.jhuapl.openessence.datasource.ui.PossibleValuesConfiguration;
import edu.jhuapl.openessence.i18n.InspectableResourceBundleMessageSource;
import edu.jhuapl.openessence.logging.LogStatements;
import edu.jhuapl.openessence.model.ChartData;
import edu.jhuapl.openessence.model.ChartModel;
import edu.jhuapl.openessence.model.DataSourceDetails;
import edu.jhuapl.openessence.model.TimeSeriesModel;
import edu.jhuapl.openessence.web.util.ControllerUtils;
import edu.jhuapl.openessence.web.util.DetailsQuery;
import edu.jhuapl.openessence.web.util.ErrorMessageException;
import edu.jhuapl.openessence.web.util.FileExportUtil;
import edu.jhuapl.openessence.web.util.Filters;
import edu.jhuapl.openessence.web.util.GraphDataBuilder;
import edu.jhuapl.openessence.web.util.Sorters;
import edu.jhuapl.openessence.web.util.TSHelper;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.Pair;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.awt.Color;
import java.awt.Font;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/report")
public class ReportController extends OeController {

    private static final int DEFAULT_LABEL_LENGTH = 45;
    private static final String PIE = "pie";
    private static final String BAR = "bar";

    private static final int DEFAULT_WEEK_STARTDAY = 1;
    private static final int DEFAULT_DAILY_PREPULL = 40;
    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final Logger translationLog = LoggerFactory.getLogger("TranslationLogger");

    private DateFormat dateFormatDayMonth = new SimpleDateFormat("MM-dd");

    private DateFormat dateFormatDay = new SimpleDateFormat("yyyy-MM-dd");
    private DateFormat dateFormatWeek = new SimpleDateFormat("yyyy-MM-dd-'W'w");
    private DateFormat dateFormatWeekPart = new SimpleDateFormat("yyyy-MM-dd");
    private DateFormat dateFormatMonth = new SimpleDateFormat("yyyy-MM");
    private DateFormat dateFormatYear = new SimpleDateFormat("yyyy");

    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";
    public static final String YEARLY = "yearly";
    public static final String TIMEZONE_ENABLED = "timezone.enabled";
    private String graphDir;

    @Resource
    private InspectableResourceBundleMessageSource messageSource;

    public static final Map<String, Integer> intervalMap = new HashMap<String, Integer>();

    @Autowired
    private TSHelper tsHelper;
    
    static {
        intervalMap.put("hourly", Calendar.HOUR_OF_DAY);
        intervalMap.put(DAILY, Calendar.DAY_OF_MONTH);
        intervalMap.put(WEEKLY, Calendar.WEEK_OF_YEAR);
        intervalMap.put(MONTHLY, Calendar.MONTH);
    }

    @Autowired
    public void setGraphDir(@Qualifier("graphDir") String graphDir) {
        this.graphDir = graphDir;
        File f = new File(this.graphDir);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                log.error("Unable to make directory " + graphDir);
            }
        }
    }

    @RequestMapping("/getFields")
    // TODO return real domain object
    public
    @ResponseBody
    Map<String, Object> getFields(@RequestParam("dsId") JdbcOeDataSource ds) throws IOException {
        final Map<String, Object> result = new HashMap<String, Object>();
        final List<DimensionConfiguration> filters = new ArrayList<DimensionConfiguration>();
        filters.addAll(getDimensionsInformation(ds.getFilterDimensions()));

        if (ds instanceof DataSeriesSource) {
            final DataSeriesSource dss = (DataSeriesSource) ds;
            // Add reserved fields, accumId, resolution field, and resolutions
            // each available 'entry' is a 'possible value' in the combo

            if (ds.getFilterDimension("accumId") != null) {

                if (!dss.getAccumulations().isEmpty()) {
                    for (final DimensionConfiguration filter : filters) {
                        if ("accumId".equals(filter.getName())) {
                            final List<List<Object>> possvals = new ArrayList<List<Object>>();
                            for (final Dimension accum : dss.getAccumulations()) {
                                final LinkedList<Object> accEntry = new LinkedList<Object>();
                                // Id then display value
                                accEntry.add(accum.getId());
                                if (accum.getDisplayName() == null) {
                                    accEntry.add(accum.getId());
                                } else {
                                    accEntry.add(accum.getDisplayName());
                                }
                                possvals.add(accEntry);
                            }

                            filter.setPossibleValues(new PossibleValuesConfiguration(possvals));
                            break;
                        }
                    }

                    // Group/Resolutions
                    final DimensionConfiguration timeseriesGroupResolution = new DimensionConfiguration();
                    timeseriesGroupResolution.setName("timeseriesGroupResolution");
                    timeseriesGroupResolution.setType(FieldType.TEXT);

                    final Map<String, Object> formMetaData = new HashMap<String, Object>();
                    formMetaData.put("allowBlank", false);
                    final Map<String, Object> metaData = new HashMap<String, Object>();
                    metaData.put("form", formMetaData);

                    timeseriesGroupResolution.setMeta(metaData);

                    final List<List<Object>> groupResolutionValues = new ArrayList<List<Object>>();
                    for (final GroupingDimension gdim : dss.getGroupingDimensions()) {
                        for (final String res : gdim.getResolutions()) {
                            final LinkedList<Object> groupResolutionEntry = new LinkedList<Object>();
                            // Id then display value
                            groupResolutionEntry.add(gdim.getId() + ":" + res);
                            groupResolutionEntry
                                    .add(messageSource.getDataSourceMessage(gdim.getId(), dss) + " / " + messageSource
                                            .getDataSourceMessage(res, dss));
                            groupResolutionValues.add(groupResolutionEntry);
                        }
                    }
                    timeseriesGroupResolution.setPossibleValues(new PossibleValuesConfiguration(groupResolutionValues));
                    filters.add(timeseriesGroupResolution);
                }
            }
        }
        result.put("filters", filters);

        //Updated detail dimensions to use new dimension configuration that populates
        //possibleValues
        List<DimensionConfiguration> detailDimensions = new ArrayList<DimensionConfiguration>();
        detailDimensions.addAll(getDimensionsInformation(ds.getResultDimensions()));
        result.put("detailDimensions", detailDimensions);

        if (ds instanceof JdbcOeDataEntrySource) {
            final JdbcOeDataEntrySource jdes = (JdbcOeDataEntrySource) ds;
            result.put("pks", jdes.getParentTableDetails().getPks());
            final ArrayList<Object> editDimensions = new ArrayList<Object>();
            editDimensions.addAll(getDimensionsInformation(jdes.getEditDimensions()));

            // Add child table information
            for (final String tableName : jdes.getChildTableMap().keySet()) {
                editDimensions.add(new ChildTableConfiguration(tableName, jdes));
            }

            result.put("editDimensions", editDimensions);
        }

        // Data source level meta data
        final Map<String, Object> meta = ds.getMetaData();
        if (meta != null) {
            result.put("meta", meta);
        }

        result.put("success", true);

        return result;
    }

    private List<DimensionConfiguration> getDimensionsInformation(final Collection<? extends Dimension> dimensions)
            throws OeDataSourceException {
        List<DimensionConfiguration> results = new ArrayList<DimensionConfiguration>();

        for (final Dimension dimension : dimensions) {
            results.add(new DimensionConfiguration(dimension));
        }
        return results;
    }

    @RequestMapping("/timeSeriesJson")
    public
    @ResponseBody
    Map<String, Object> timeSeriesJson(@RequestParam("dsId") JdbcOeDataSource ds, TimeSeriesModel model,
                                       Principal principal, WebRequest request, HttpServletRequest servletRequest)
            throws ErrorMessageException {

        if (ds.getDimensionJoiner() != null) {
            ds.getDimensionJoiner().joinDimensions();
        }

        // if we are doing each year as it's own series
        // update start and end date based on selected accum value
        if (model.isYearAsSeries()) {
            return createEachYearAsSeries(ds, model, principal, request, servletRequest);
        }
        
        DataSeriesSource dss = (ds instanceof DataSeriesSource) ? (DataSeriesSource) ds : null;
        
        String groupId = model.getGroupId();
        GroupingDimension groupingDim = dss.getGroupingDimension(groupId);
        String resolution = model.getResolution(groupingDim, groupId);
        
        List<Dimension> accumulations = ControllerUtils.getAccumulationsByIds(ds, model.getAccumId());
        List<Dimension> timeseriesDenominators =
                ControllerUtils.getAccumulationsByIds(ds, model.getTimeseriesDenominator(), false);

        GroupingImpl group = new GroupingImpl(groupId, resolution);

        if(resolution.equals(DAILY) && model.getPrepull() < 0) {
            model.setPrepull(DEFAULT_DAILY_PREPULL);
        }
        if (model.getPrepull() < 0) {
            model.setPrepull(0);
        }

        //union accumulations to get all results
        List<Dimension> dimensions = new ArrayList<Dimension>(ControllerUtils.unionDimensions(accumulations,
                                                                                              timeseriesDenominators));
        List<Grouping> groupings = new ArrayList<Grouping>();
        groupings.add(groupingDim.makeGrouping(resolution));

        //create results group dimension + all dimensions
        final List<Dimension> results = new ArrayList<Dimension>();
        for (Dimension d : dimensions) {
            results.add(ds.getResultDimension(d.getId()));
        }

        Map<String, String[]> params = request.getParameterMap();
        
        Map<String, ResolutionHandler> resolutionHandlers = dss.getGroupingDimension(group.getId()).getResolutionsMap();
        List<Filter> filters = new Filters().getFilters(params, dss, group.getId(),
                                                        model.getPrepull(), resolution,
                                                        getCalWeekStartDay(resolutionHandlers));
        //details query for all records
        Collection<Record> records =
                new DetailsQuery().performDetailsQuery(ds, results, dimensions, filters,  new ArrayList<OrderByFilter>(), groupings, false,
                    tsHelper.getClientTimezone(request, messageSource));

        String graphTimeSeriesUrl =
                tsHelper.buildTimeSeriesURL(ds, request.getContextPath(), servletRequest.getServletPath(),
                        messageSource);

        return createTimeseries(principal.getName(), dss, filters, group, resolution, model, records, accumulations,
                        timeseriesDenominators, graphTimeSeriesUrl, ControllerUtils.getRequestTimezone(request));
    }

    @RequestMapping("/chartJson")
    public
    @ResponseBody
    Map<String, Object> chartJson(WebRequest request, HttpServletRequest servletRequest,
                                  @RequestParam("dsId") JdbcOeDataSource ds, ChartModel chartModel)
            throws ErrorMessageException {

        log.info(LogStatements.GRAPHING.getLoggingStmt() + request.getUserPrincipal().getName());

        final List<Filter> filters = new Filters().getFilters(request.getParameterMap(), ds, null, 0, null, 0);
        final List<Dimension> results =
                ControllerUtils.getResultDimensionsByIds(ds, request.getParameterValues("results"));

        Dimension filterDimension = null;
        if (results.get(0).getFilterBeanId() != null && results.get(0).getFilterBeanId().length() > 0) {
            filterDimension = ds.getFilterDimension(results.get(0).getFilterBeanId());
        }
        // if not provided, use the result dimension
        // it means name and id columns are same...
        if (filterDimension != null) {
            results.add(results.size(), filterDimension);
        }

        // Subset of results, should check
        final List<Dimension> charts =
                ControllerUtils.getResultDimensionsByIds(ds, request.getParameterValues("charts"));

        final List<Dimension> accumulations =
                ControllerUtils.getAccumulationsByIds(ds, request.getParameterValues("accumId"));

        final List<OrderByFilter> sorts = new ArrayList<OrderByFilter>();
        try {
            sorts.addAll(Sorters.getSorters(request.getParameterMap()));
        } catch (Exception e) {
            log.warn("Unable to get sorters, using default ordering");
        }

        // TODO put this on ChartModel
        //default to white allows clean copy paste of charts from browser
        Color backgroundColor = Color.WHITE;

        String bgParam = request.getParameter("backgroundColor");
        if (bgParam != null && !"".equals(bgParam)) {
            if ("transparent".equalsIgnoreCase(bgParam)) {
                backgroundColor = new Color(255, 255, 255, 0);
            } else {
                backgroundColor = ControllerUtils.getColorsFromHex(Color.WHITE, bgParam)[0];
            }
        }

        String graphBarUrl = request.getContextPath() + servletRequest.getServletPath() + "/report/graphBar";
        graphBarUrl = tsHelper.appendGraphFontParam(ds, graphBarUrl, messageSource);

        String graphPieUrl = request.getContextPath() + servletRequest.getServletPath() + "/report/graphPie";
        graphPieUrl = tsHelper.appendGraphFontParam(ds, graphPieUrl, messageSource);

        // TODO eliminate all the nesting in response and just use accumulation and chartID properties
        Map<String, Object> response = new HashMap<String, Object>();
        Map<String, Object> graphs = new HashMap<String, Object>();
        response.put("graphs", graphs);

        String clientTimezone = null;
        String timezoneEnabledString = messageSource.getMessage(TIMEZONE_ENABLED, "false");
        if (timezoneEnabledString.equalsIgnoreCase("true")) {
            clientTimezone = ControllerUtils.getRequestTimezoneAsHourMinuteString(request);
        }
        Collection<Record> records = new DetailsQuery().performDetailsQuery(ds, results, accumulations,
                                                                            filters, sorts, false, clientTimezone);
        final List<Filter> graphFilters =
                new Filters().getFilters(request.getParameterMap(), ds, null, 0, null, 0, false);
        //for each requested accumulation go through each requested result and create a chart
        for (Dimension accumulation : accumulations) {
            Map<String, Object> accumulationMap = new HashMap<String, Object>();
            // Create charts for dimensions (subset of results)
            for (Dimension chart : charts) {
                DefaultGraphData data = new DefaultGraphData();
                data.setGraphTitle(chartModel.getTitle());
                data.setGraphHeight(chartModel.getHeight());
                data.setGraphWidth(chartModel.getWidth());
                data.setShowLegend(chartModel.isLegend());
                data.setBackgroundColor(backgroundColor);
                data.setShowGraphLabels(chartModel.isShowGraphLabels());
                data.setLabelBackgroundColor(backgroundColor);
                data.setPlotHorizontal(chartModel.isPlotHorizontal());
                data.setNoDataMessage(chartModel.getNoDataMessage());
                data.setTitleFont(new Font("Arial", Font.BOLD, 12));

                GraphObject graph = createGraph(ds, request.getUserPrincipal().getName(), records, chart,
                                                filterDimension, accumulation, data, chartModel, graphFilters);
                String graphURL = "";
                if (BAR.equalsIgnoreCase(chartModel.getType())) {
                    graphURL = graphBarUrl;
                } else if (PIE.equalsIgnoreCase(chartModel.getType())) {
                    graphURL = graphPieUrl;
                }
                graphURL = appendUrlParameter(graphURL, "graphDataId", graph.getGraphDataId());

                chartModel.setImageUrl(graphURL);
                chartModel.setImageMap(graph.getImageMap());
                chartModel.setImageMapName(graph.getImageMapName());

                accumulationMap.put(chart.getId(), chartModel);
            }
            graphs.put(accumulation.getId(), accumulationMap);
        }

        log.info(String.format("Chart JSON Details query for %s", request.getUserPrincipal().getName()));

        return response;
    }

    private boolean isEpiWeekEnabled() {
        return "0"
                .equals(messageSource.getMessage("epidemiological.day.start", Integer.toString(DEFAULT_WEEK_STARTDAY)))
                && "1".equals(messageSource.getMessage("use.cdc.epiweek", "0"));
    }
    
    private Map<String, Object> createEachYearAsSeries(JdbcOeDataSource ds, TimeSeriesModel model, Principal principal,
            WebRequest request, HttpServletRequest servletRequest) throws ErrorMessageException {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("success", false);

        DataSeriesSource dss = (ds instanceof DataSeriesSource) ? (DataSeriesSource) ds : null;

        String groupId = model.getGroupId();
        GroupingDimension groupingDim = dss.getGroupingDimension(groupId);
        String timeResolution = model.getResolution(groupingDim, groupId);
        GroupingImpl group = new GroupingImpl(groupId, timeResolution);
        List<Grouping> groupings = new ArrayList<Grouping>();
        groupings.add(groupingDim.makeGrouping(timeResolution));

        model.setPrepull(0);

        TimeZone clientTimezone = ControllerUtils.getRequestTimezone(request);
        int timeOffsetMillies = tsHelper.getTimezoneOffsetMillies(messageSource, clientTimezone);

        List<Dimension> selectedAccumulations = ControllerUtils.getAccumulationsByIds(ds, model.getAccumId());
        Map<String, ResolutionHandler> resolutionHandlers = dss.getGroupingDimension(group.getId()).getResolutionsMap();

        String graphTimeSeriesUrl = tsHelper.buildTimeSeriesURL(ds, request.getContextPath(), servletRequest.getServletPath(),
                        messageSource);

        GraphDataBuilder graphDataBuilder = new GraphDataBuilder(selectedAccumulations.size());
        String[][] labels = new String[selectedAccumulations.size()][];
        
        // -- Handles Denominator Types -- //
        List<Dimension> timeseriesDenominators = ControllerUtils.getAccumulationsByIds(ds,
            model.getTimeseriesDenominator(), false);
        boolean percentBased = timeseriesDenominators != null && !timeseriesDenominators.isEmpty();
        double multiplier = percentBased ? 100.0 : 1.0;
        
        
        boolean isDetectionDetector = !NoDetectorDetector.class.getName().equalsIgnoreCase(
                model.getTimeseriesDetectorClass());
        // for each accumulation we run detection and gather results
        int accumIndex = 0;
        boolean epiWeekEnabled = isEpiWeekEnabled();
        boolean dataFound = false;
        
        // number format for level
        NumberFormat numFormat3 = tsHelper.getNumberFormat(3);
        // number format for expected count
        NumberFormat numFormat1 = tsHelper.getNumberFormat(1);

        // Accumulation will be year
        // for each year run TS query and plot results
        for (Dimension accumulation : selectedAccumulations) {
            List<Dimension> accumulations = new ArrayList<Dimension>();
            accumulations.add(accumulation);

            // union accumulations to get all results
            List<Dimension> dimensions = new ArrayList<Dimension>(ControllerUtils.unionDimensions(accumulations,
                    timeseriesDenominators));

            // create results group dimension + all dimensions
            final List<Dimension> results = new ArrayList<Dimension>();
            for (Dimension d : dimensions) {
                results.add(ds.getResultDimension(d.getId()));
            }

            // add start and end dates for this accumulation/year
            Map<String, String[]> params = tsHelper.fixStartEndDatesForYearAsSeries(request.getParameterMap(),
                    accumulation, groupId, timeResolution, epiWeekEnabled);

            List<Filter> filters = new Filters().getFilters(params, dss, group.getId(), model.getPrepull(),
                    timeResolution, getCalWeekStartDay(resolutionHandlers));

            Pair<Date, Date> startEndDatePair = tsHelper.getStartEndDates(groupingDim, filters);
            
            // details query for all records
            Collection<Record> records = new DetailsQuery().performDetailsQuery(ds, results, dimensions, filters,
                new ArrayList<OrderByFilter>(), groupings, false, tsHelper.getClientTimezone(request, messageSource));

            Calendar startDayCal = Calendar.getInstance(clientTimezone);
            startDayCal.setTime(startEndDatePair.getFirst());
            startDayCal.add(Calendar.MILLISECOND, timeOffsetMillies);

            // get data grouped by group dimension
            List<AccumPoint> points = extractAccumulationPoints(principal.getName(), dss, records,
                    startDayCal.getTime(), startEndDatePair.getSecond(), dimensions, group, resolutionHandlers);
            
            String accumIdTranslated = (null != accumulation.getDisplayName()) ? accumulation.getDisplayName() : messageSource
                            .getDataSourceMessage(accumulation.getId(), dss);

            if (points.size() > 0) {
                dataFound = true;
                DateFormat dateFormat = getDateFormat(timeResolution, clientTimezone);
                double[] divisors = tsHelper.getDivisors(points, timeseriesDenominators);

                // get all results
                Collection<Dimension> dims = new ArrayList<Dimension>(dss.getResultDimensions());
                Collection<String> dimIds = ControllerUtils.getDimensionIdsFromCollection(dims);
                Collection<String> accIds = ControllerUtils.getDimensionIdsFromCollection(dss.getAccumulations());
                // remove extra accumulations in the result set using string ids
                dimIds.removeAll(accIds);

                // pull the counts from the accum array points
                double[] seriesDoubleArray =
                        tsHelper.generateSeriesValues(points, accumulation.getId(), divisors, multiplier);
                TemporalDetectorSimpleDataObject TDDO;
                try {
                    TDDO = tsHelper.runDetection(startDayCal, startEndDatePair, seriesDoubleArray, model,
                                    timeResolution);
                } catch (DetectorException e) {
                    return tsHelper.setDetectionErrorMessage(e);
                }
                double[] counts = TDDO.getCounts();
                double[] tcolors = TDDO.getColors();
                Date[] tdates = TDDO.getDates();
                String[] altTexts = TDDO.getAltTexts();
                double[] expecteds = TDDO.getExpecteds();
                double[] levels = TDDO.getLevels();
                String[] switchInfo = TDDO.getSwitchInfo();
                int[] colors = new int[counts.length];
                String[] dates = new String[counts.length];
                String[] urls = new String[counts.length];
                String[] xLabels = new String[counts.length];

                // add the accumId for the current series
                dimIds.add(accumulation.getId());
                StringBuilder jsCall = initJsCall(dss, dimIds, accumulation.getId(), group.getId(), filters);
                
                // this builds urls and hover texts
                int startDay = getWeekStartDay(resolutionHandlers);
                Calendar tmpCal = Calendar.getInstance(clientTimezone);

                for (int i = 0; i < counts.length; i++) {
                    colors[i] = (int) tcolors[i];

                    // For a time series data point, set time to be current server time
                    // This will allow us to convert this data point date object
                    // to be request timezone date
                    tmpCal.setTime(tdates[i]);
                    tmpCal.add(Calendar.MILLISECOND, timeOffsetMillies);

                    if (timeResolution.equals(WEEKLY)) {
                        int weekNum = PgSqlDateHelper.getWeekOfYear(startDay, tmpCal);
                        int yearNum = PgSqlDateHelper.getYear(startDay, tmpCal);
                        if (epiWeekEnabled) {
                            weekNum = PgSqlDateHelper.getEpiWeek(tmpCal);
                            yearNum = PgSqlDateHelper.getEpiYear(tmpCal);
                        }
                        dates[i] = dateFormatWeekPart.format(tdates[i]) + "-W" + weekNum + "-" + yearNum;
                        xLabels[i] = "W" + (i + 1);
                    } else {
                        dates[i] = dateFormat.format(tmpCal.getTime());
                        xLabels[i] = dateFormatDayMonth.format(tmpCal.getTime());
                    }

                    altTexts[i] = "(" + accumIdTranslated + ") " + // Accum
                            "Date: " + dates[i] + // Date
                            ", Level: " + numFormat3.format(levels[i]) + // Level
                            ", Count: " + ((int) counts[i]) + // Count
                            ", Expected: " + numFormat1.format(expecteds[i]); // Expected

                    if (switchInfo != null) {
                        altTexts[i] += ", Switch: " + switchInfo[i] + ", ";
                    }

                    urls[i] = tsHelper.buildDetailsURL(group.getId(), timeResolution, clientTimezone, tdates[i],
                                    startDay, jsCall);
                }

                graphDataBuilder.setDataSeriesInfo(accumIndex, counts, colors, altTexts, expecteds, levels, urls,
                        switchInfo, accumIdTranslated, isDetectionDetector);
                labels[accumIndex] = xLabels;
            } else {
                graphDataBuilder.setDataSeriesInfo(accumIndex, new double[0], new int[0], new String[0], new double[0],
                        new double[0], new String[0], new String[0], accumIdTranslated, isDetectionDetector);
                labels[accumIndex] = new String[0];
            }
            accumIndex++;
        }
        if (dataFound) {
            // create graph data and set known configuration
            DefaultGraphData graphData = model.initGraphData();

            graphDataBuilder.fixDataLengths(labels);
            graphDataBuilder.updateGraphData(graphData);
            
            graphData.setShowSingleAlertLegends(isDetectionDetector);
            graphData.setPercentBased(percentBased);

            String xAxisLabel = messageSource.getDataSourceMessage(group.getResolution(), dss);
            String yAxisLabel = messageSource.getDataSourceMessage(percentBased ? "graph.percent" : "graph.count", dss);
            graphData.setXAxisLabel(xAxisLabel);
            graphData.setYAxisLabel(yAxisLabel);

            if (model.isIncludeDetails()) {
                tsHelper.addDetailsToResult(result, graphDataBuilder);
            }

            GraphDataSerializeToDiskHandler hndl = new GraphDataSerializeToDiskHandler(graphDir);
            GraphController gc = getGraphController(null, hndl, principal.getName());
            // TODO figure out why I (hodancj1) added this to be accumulation
            // size ~Feb 2012
            // gc.setMaxLegendItems(accumulations.size());

            try {
                tsHelper.addGraphConfigToResult(result, gc, graphData, graphTimeSeriesUrl,
                        graphDataBuilder.getAllCounts(), model.isGraphExpectedValues());
            } catch (IOException e) {
                log.error("Failure to create Timeseries", e);
            }
            result.put("success", true);
        } else {
            result = tsHelper.buildNoDataResult(dss, messageSource);
        }
        return result;
    }
    
    private Map<String, Object> createTimeseries(String userPrincipalName, DataSeriesSource dss, List<Filter> filters,
            GroupingImpl group, String timeResolution, TimeSeriesModel model, final Collection<Record> records,
            final List<Dimension> accumulations, final List<Dimension> timeseriesDenominators,
            final String graphTimeSeriesUrl, TimeZone clientTimezone) {

        Map<String, Object> result = new HashMap<String, Object>();
        Map<String, ResolutionHandler> resolutionHandlers = null;
        result.put("success", false);
        try {
            GroupingDimension grpdim = dss.getGroupingDimension(group.getId());
            resolutionHandlers = grpdim.getResolutionsMap();
            String dateFieldName = group.getId();
            Pair<Date, Date> startEndDatePair = tsHelper.getStartEndDates(grpdim, filters);
            
            //union accumulations to get all results
            List<Dimension> dimensions =
                    new ArrayList<Dimension>(ControllerUtils.unionDimensions(accumulations, timeseriesDenominators));

            int timeOffsetMillies = tsHelper.getTimezoneOffsetMillies(messageSource, clientTimezone);
            Calendar startDayCal = Calendar.getInstance(clientTimezone);
            startDayCal.setTime(startEndDatePair.getFirst());
            startDayCal.add(Calendar.MILLISECOND, timeOffsetMillies);

            //get data grouped by group dimension
            List<AccumPoint> points = extractAccumulationPoints(userPrincipalName, dss, records, startDayCal.getTime(), startEndDatePair.getSecond(),
                dimensions, group, resolutionHandlers);
            if (points.size() > 0) {
                // number format for level
                NumberFormat numFormat3 = tsHelper.getNumberFormat(3);
                // number format for expected count
                NumberFormat numFormat1 = tsHelper.getNumberFormat(1);

                DateFormat dateFormat = getDateFormat(timeResolution, clientTimezone); 
                
                //-- Handles Denominator Types -- //
                double[] divisors = tsHelper.getDivisors(points, timeseriesDenominators);
                boolean percentBased = timeseriesDenominators != null && !timeseriesDenominators.isEmpty();
                double multiplier = percentBased ? 100.0 : 1.0;
                boolean isDetectionDetector =
                        !NoDetectorDetector.class.getName().equalsIgnoreCase(model.getTimeseriesDetectorClass());

                GraphDataBuilder graphDataBuilder = new GraphDataBuilder(accumulations.size());

                //get all results
                Collection<String> dimIds = ControllerUtils.getDimensionIdsFromCollection(dss.getResultDimensions());
                Collection<String> accIds = ControllerUtils.getDimensionIdsFromCollection(dss.getAccumulations());
                //remove extra accumulations in the result set using string ids
                dimIds.removeAll(accIds);

                //for each accumulation we run detection and gather results
                int aIndex = 0;
                for (Dimension accumulation : accumulations) {
                    String accumId = accumulation.getId();

                    // use display name if it has one, otherwise translate its ID
                    String accumIdTranslated = accumulation.getDisplayName();
                    if (accumIdTranslated == null) {
                        accumIdTranslated = messageSource.getDataSourceMessage(accumulation.getId(), dss);
                    }

                    //pull the counts from the accum array points
                    double[] seriesDoubleArray = tsHelper.generateSeriesValues(points, accumId, divisors, multiplier);

                    TemporalDetectorSimpleDataObject TDDO;
                    try {
                        TDDO = tsHelper.runDetection(startDayCal, startEndDatePair, seriesDoubleArray, model, timeResolution);
                    } catch (DetectorException e) {
                        return tsHelper.setDetectionErrorMessage(e);
                    }

                    double[] counts = TDDO.getCounts();
                    double[] tcolors = TDDO.getColors();
                    String[] dates = new String[counts.length];
                    Date[] tdates = TDDO.getDates();
                    String[] altTexts = TDDO.getAltTexts();
                    double[] expecteds = TDDO.getExpecteds();
                    double[] levels = TDDO.getLevels();
                    String[] switchInfo = TDDO.getSwitchInfo();
                    int[] colors = new int[counts.length];
                    String[] urls = new String[counts.length];

                    //add the accumId for the current series
                    dimIds.add(accumId);
                    StringBuilder jsCall = initJsCall(dss, dimIds, accumId, dateFieldName, filters);
                    
                    //this builds urls and hover texts
                    int startDay = getWeekStartDay(resolutionHandlers);

                    Calendar tmpCal = Calendar.getInstance(clientTimezone);
                    for (int i = 0; i < counts.length; i++) {
                        colors[i] = (int) tcolors[i];

                        // For a time series data point, set time to be current server time
                        // This will allow us to convert this data point date object to be request timezone date
                        tmpCal.setTime(tdates[i]);
                        tmpCal.add(Calendar.MILLISECOND, timeOffsetMillies);

                        if (timeResolution.equals(WEEKLY)) {
                            dates[i] = dateFormatWeekPart.format(tdates[i])
                                       + "-W" + PgSqlDateHelper.getWeekOfYear(startDay, tmpCal) + "-"
                                       + PgSqlDateHelper.getYear(startDay, tmpCal);
                        } else {
                            dates[i] = dateFormat.format(tmpCal.getTime());
                        }

                        altTexts[i] = "(" + accumIdTranslated + ") " + // Accum
                                      "Date: " + dates[i] + // Date
                                      ", Level: " + numFormat3.format(levels[i]) + // Level
                                      ", Count: " + ((int) counts[i]) + // Count
                                      ", Expected: " + numFormat1.format(expecteds[i]); // Expected

                        if (switchInfo != null) {
                            altTexts[i] += ", Switch: " + switchInfo[i] + ", ";
                        }
                       
                        urls[i] = tsHelper.buildDetailsURL(dateFieldName, timeResolution, clientTimezone, 
                                tdates[i], startDay, jsCall);
                    }

                    graphDataBuilder.setDataSeriesInfo(aIndex, counts, colors, altTexts, expecteds, levels, urls, switchInfo,
                            accumIdTranslated, isDetectionDetector);
                    graphDataBuilder.setXAxisLabels(dates);

                    aIndex++;

                    //remove the accumId for the next series
                    dimIds.remove(accumId);
                }

                //create graph data and set known configuration
                DefaultGraphData graphData = model.initGraphData();
                graphDataBuilder.updateGraphData(graphData);
                graphData.setShowSingleAlertLegends(isDetectionDetector);
                graphData.setPercentBased(percentBased);

                String xAxisLabel = messageSource.getDataSourceMessage(group.getResolution(), dss);
                String yAxisLabel =
                        messageSource.getDataSourceMessage(percentBased ? "graph.percent" : "graph.count", dss);

                graphData.setXAxisLabel(xAxisLabel);
                graphData.setYAxisLabel(yAxisLabel);
               
                if (model.isIncludeDetails()) {
                    tsHelper.addDetailsToResult (result, graphDataBuilder);
                }
                
                GraphDataSerializeToDiskHandler hndl = new GraphDataSerializeToDiskHandler(graphDir);
                GraphController gc = getGraphController(null, hndl, userPrincipalName);
                //TODO figure out why I (hodancj1) added this to be accumulation size ~Feb 2012
                // gc.setMaxLegendItems(accumulations.size());

                tsHelper.addGraphConfigToResult(result, gc, graphData, graphTimeSeriesUrl, graphDataBuilder.getAllCounts(), model.isGraphExpectedValues());
                result.put("success", true);
            } else {
               result = tsHelper.buildNoDataResult(dss, messageSource);
            }
        } catch (Exception e) {
            log.error("Failure to create Timeseries", e);
        }
        return result;
    }

    protected DateFormat getDateFormat(String timeResolution, TimeZone clientTimezone) {
        DateFormat dateFormat = dateFormatDay;
        String formatKey = "java.date.formatDay";
        String formatError = "[" + formatKey + "]";
        String formatValue = "";
        if (DAILY.equalsIgnoreCase(timeResolution)) {
            formatKey = "java.date.formatDay";
            dateFormat = dateFormatDay;
        } else if (WEEKLY.equalsIgnoreCase(timeResolution)) {
            formatKey = "java.date.formatWeek";
            dateFormat = dateFormatWeek;
        } else if (MONTHLY.equalsIgnoreCase(timeResolution)) {
            formatKey = "java.date.formatMonth";
            dateFormat = dateFormatMonth;
        } else if (YEARLY.equalsIgnoreCase(timeResolution)) {
            formatKey = "java.date.formatYear";
            dateFormat = dateFormatYear;
        }
        formatError = "[" + formatKey + "]";
        formatValue = messageSource.getMessage(formatKey);

        if (!"".equals(formatValue) && !formatError.equals(formatValue)) {
            try {
                dateFormat = new SimpleDateFormat(formatValue);
            } catch (Exception ex) {
                translationLog.error("Error parsing " + formatKey + " into a DateFormat", ex);
            }
        }
        DateFormat tmpDateFormat = (DateFormat) dateFormat.clone();
        tmpDateFormat.setTimeZone(clientTimezone);

        return tmpDateFormat;
    }

    private GraphObject createGraph(OeDataSource dataSource, final String userPrincipalName,
                                    final Collection<Record> records,
                                    final Dimension dimension, final Dimension filter, final Dimension accumulation,
                                    DefaultGraphData data,
                                    ChartModel chart, List<Filter> filters) {

        String filterId = (filter == null) ? dimension.getId() : filter.getId();
        Map<String, String> possibleKeyValueMap = null;
        if (dimension.getPossibleValuesConfiguration() != null
            && dimension.getPossibleValuesConfiguration().getData() != null) {
            List<List<Object>> dataMap = dimension.getPossibleValuesConfiguration().getData();
            possibleKeyValueMap = new HashMap<String, String>();
            for (int i = 0; i < dataMap.size(); i++) {
                String
                        dispVal =
                        dataMap.get(i).size() == 2 ? dataMap.get(i).get(1).toString()
                                                   : dataMap.get(i).get(0).toString();
                possibleKeyValueMap.put(dataMap.get(i).get(0).toString(), dispVal);
            }
        }

        GraphDataSerializeToDiskHandler hndl = new GraphDataSerializeToDiskHandler(graphDir);
        GraphObject graph = null;

        Color[] colorsFromHex = null;
        //only set an array if they provided one
        if (!ArrayUtils.isEmpty(chart.getGraphBaseColors())) {
            colorsFromHex = ControllerUtils.getColorsFromHex(Color.BLUE, chart.getGraphBaseColors());
            //TODO when we limit the series these colors need augmented.  Create a map of id = graphbasecolor[index] first, then use that map to create a
            //new graph base color array that combines the parameter list with the default list...
            data.setGraphBaseColors(colorsFromHex);
        }

        GraphController gc = getGraphController(null, hndl, userPrincipalName);

        List<Record> recs = new ArrayList<Record>(records);

        String otherLabel = messageSource.getDataSourceMessage("graph.category.other", dataSource);

        LinkedHashMap<String, ChartData>
                recordMap =
                getRecordMap(recs, accumulation.getId(), dimension.getId(), filterId);
        //perform series limit
        recordMap = ControllerUtils.getSortedAndLimitedChartDataMap(recordMap, chart.getCategoryLimit(), otherLabel);

        //if there is no data (all zeros for a pie chart) the chart will not display anything
        if (!ControllerUtils.isCollectionValued(getCountsForChart(recordMap)) && !chart.isShowNoDataGraph()) {
            //this will hide the title and message if there is no data
            data.setGraphTitle("");
            data.setNoDataMessage("");
        }

        // Create urls for each slice/bar
        DataSeriesSource dss = null;
        StringBuilder jsCall = new StringBuilder();
        jsCall.append("javascript:OE.report.datasource.showDetails({");
        
        if (dataSource instanceof DataSeriesSource) {
            dss = (DataSeriesSource) dataSource;

            Collection<Dimension> dims = new ArrayList<Dimension>(dss.getResultDimensions());
            Collection<String> dimIds = ControllerUtils.getDimensionIdsFromCollection(dims);

            Collection<Dimension> accums = new ArrayList<Dimension>(dss.getAccumulations());

            for (Dimension d : accums) {
                if (dimIds.contains(d.getId()) && d.getId().equals(accumulation.getId())) {

                } else {
                    dimIds.remove(d.getId());
                }
            }
            jsCall = initJsCall(dss, dimIds, accumulation.getId(), dimension.getId(), filters);
        }

        int rSize = recordMap.size();
        int aSize = 1;
        String[] lbl = new String[rSize];
        String[][] txtb = new String[1][rSize];
        double[][] bardat = new double[aSize][rSize];
        String[][] txtp = new String[rSize][1];
        double[][] piedat = new double[rSize][aSize];
        String[][] urlsP = new String[rSize][1];
        String[][] urlsB = new String[1][rSize];
        int i = 0;
        double totalCount = 0;
        DecimalFormat df = new DecimalFormat("#.##");

        for (String key : recordMap.keySet()) {
            if (recordMap.get(key) != null && recordMap.get(key).getCount() != null && !recordMap.get(key).getCount()
                    .isNaN()) {
                totalCount += recordMap.get(key).getCount();
            }
        }

        for (String key : recordMap.keySet()) {
            Double dubVal = recordMap.get(key).getCount();
            String strPercentVal = df.format(100 * dubVal / totalCount);
            lbl[i] = recordMap.get(key).getName();
            //create bar data set
            bardat[0][i] = dubVal;
            txtb[0][i] = lbl[i] + " - " + Double.toString(dubVal) + " (" + strPercentVal + "%)";
            if (lbl[i].length() > DEFAULT_LABEL_LENGTH) {
                lbl[i] = lbl[i].substring(0, DEFAULT_LABEL_LENGTH - 3) + "...";
            }
            //create pie data set
            piedat[i][0] = dubVal;
            txtp[i][0] = lbl[i] + " - " + Double.toString(dubVal) + " (" + strPercentVal + "%)";
            if (lbl[i].length() > DEFAULT_LABEL_LENGTH) {
                lbl[i] = lbl[i].substring(0, DEFAULT_LABEL_LENGTH - 3) + "...";
            }
            //TODO all "Others" to return details of all results except for those in recordMap.keyset
            //We need a "Not" filter
            if (!otherLabel.equals(key)) {
                if (dataSource instanceof DataSeriesSource) {
                    if (dimension.getId().equals(filterId) && possibleKeyValueMap != null) {
                        if (possibleKeyValueMap.containsKey(key)) {
                            urlsP[i][0] = jsCall.toString() + "," + filterId + ":'" + key + "'" + "});";
                            urlsB[0][i] = jsCall.toString() + "," + filterId + ":'" + key + "'" + "});";
                        } else {
                            urlsP[i][0] = jsCall.toString() + "});";
                            urlsB[0][i] = jsCall.toString() + "});";
                        }
                    } else {
                        if (key == null || key.equals("") || key
                                .equals(messageSource.getMessage("graph.dimension.null", "Empty Value"))) {
                            // TODO: This is when we have an ID field also marked as isResult:true and the value is null
                            // We can not provide url param filterId:null as field can be numeric and we get a java.lang.NumberFormatException...
                            urlsP[i][0] = jsCall.toString() + "});";
                            urlsB[0][i] = jsCall.toString() + "});";
                        } else {
                            urlsP[i][0] = jsCall.toString() + "," + filterId + ":'" + key + "'" + "});";
                            urlsB[0][i] = jsCall.toString() + "," + filterId + ":'" + key + "'" + "});";
                        }
                    }
                }
            }

            i++;
        }

        if (BAR.equalsIgnoreCase(chart.getType())) {
            data.setCounts(bardat);
            data.setXLabels(lbl);
            data.setMaxLabeledCategoryTicks(rSize);
            data.setAltTexts(txtb);
            if (jsCall.length() > 0) {
                data.setLineSetURLs(urlsB);
            }
            //TODO add encoding?
            graph = gc.createBarGraph(data, false, true);
        } else if (PIE.equalsIgnoreCase(chart.getType())) {
            data.setCounts(piedat);
            data.setLineSetLabels(lbl);
            data.setAltTexts(txtp);
            if (jsCall.length() > 0) {
                data.setLineSetURLs(urlsP);
            }
            graph = gc.createPieGraph(data, Encoding.PNG_WITH_TRANSPARENCY);
        }
        return graph;
    }

    private Collection<Double> getCountsForChart(LinkedHashMap<String, ChartData> recordMap) {
        Collection<Double> counts = new ArrayList<Double>();
        for (String id : recordMap.keySet()) {
            counts.add(recordMap.get(id).getCount());
        }

        return counts;
    }

    private LinkedHashMap<String, ChartData> getRecordMap(List<Record> records, String accumId, String dimensionId,
                                                          String filterId) {
        LinkedHashMap<String, ChartData> map = new LinkedHashMap<String, ChartData>(records.size());
        int rSize = records.size();
        for (int i = 0; i < rSize; i++) {
            Record record = records.get(i);
            Object accumValue = record.getValue(accumId);
            Object dimenValue = record.getValue(dimensionId);
            Object filterValue = record.getValue(filterId == null ? dimensionId : filterId);
            String dimenString = "";
            String filterString = "";
            if (dimenValue != null) {
                dimenString = String.valueOf(dimenValue);
            } else {
                dimenString = messageSource.getMessage("graph.dimension.null");
            }
            if (filterValue != null) {
                filterString = convertFilter(filterValue);
            } else {
                filterString = messageSource.getMessage("graph.dimension.null");
            }
            try {
                Double dubVal = Double.NaN;
                if (accumValue != null) {
                    dubVal = Double.valueOf(accumValue.toString());
                }
                map.put(filterString, new ChartData(filterString, dimenString, dubVal));
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return map;
    }

    @RequestMapping("/graphTimeSeries")
    public void graphTimeSeries(HttpServletRequest req, HttpServletResponse resp,
                                @RequestParam("graphDataId") String dataId,
                                @RequestParam(required = false) String graphTitle,
                                @RequestParam(required = false) String xAxisLabel,
                                // TODO put these all in a graph model object and let Spring deserialize from JSON
                                @RequestParam(required = false) String yAxisLabel,
                                @RequestParam(required = false) Double yAxisMin,
                                @RequestParam(required = false) Double yAxisMax,
                                @RequestParam(required = false) String dataDisplayKey,
                                @RequestParam(required = false) String getImageMap,
                                @RequestParam(required = false) String imageType,
                                @RequestParam(required = false) String resolution,
                                @RequestParam(required = false) String getHighResFile,
                                @RequestParam(required = false) boolean graphExpectedValues)
            throws GraphException, IOException {

        GraphDataSerializeToDiskHandler hndl = new GraphDataSerializeToDiskHandler(graphDir);
        GraphController gc = getGraphController(dataId, hndl, req.getUserPrincipal().getName());

        GraphDataInterface data = hndl.getGraphData(dataId);

        if (graphTitle != null) {
            data.setGraphTitle(graphTitle);
        }
        if (xAxisLabel != null) {
            data.setXAxisLabel(xAxisLabel);
        }
        if (yAxisLabel != null) {
            data.setYAxisLabel(yAxisLabel);
        }

        GraphObject graph = gc.createTimeSeriesGraph(data, yAxisMin, yAxisMax, dataDisplayKey, graphExpectedValues);
        BufferedOutputStream out = new BufferedOutputStream(resp.getOutputStream());

        if (getImageMap != null && (getImageMap.equals("1") || getImageMap.equalsIgnoreCase("true"))) {
            resp.setContentType("text/plain;charset=utf-8");
            StringBuffer sb = new StringBuffer();
            sb.append(graph.getImageMap());
            out.write(sb.toString().getBytes());
        } else {
            resp.setContentType("image/png;charset=utf-8");
            String filename = graph.getImageFileName();
            filename = filename.replaceAll("\\s", "_");
            resp.setHeader("Content-disposition", "attachment; filename=" + filename);
            int imageResolution = 300;
            if (resolution != null) {
                try {
                    imageResolution = Integer.parseInt(resolution);
                    graph.writeChartAsHighResolutionPNG(out, data.getGraphWidth(), data.getGraphHeight(),
                                                        imageResolution);
                } catch (Exception e) {
                    log.error("", e);
                }
            } else {
                graph.writeChartAsPNG(out, data.getGraphWidth(), data.getGraphHeight());
            }
        }
    }

    @RequestMapping("/graphBar")
    public void graphBar(HttpServletRequest req, HttpServletResponse resp,
                         @RequestParam("graphDataId") String dataId,
                         @RequestParam(required = false) Integer resolution) throws GraphException, IOException {
        GraphDataSerializeToDiskHandler hndl = new GraphDataSerializeToDiskHandler(graphDir);
        GraphController gc = getGraphController(dataId, hndl, req.getUserPrincipal().getName());

        GraphDataInterface data = hndl.getGraphData(dataId);
        GraphObject graph = gc.createBarGraph(data, false);
        String filename = graph.getImageFileName();
        filename = filename.replaceAll("\\s", "_");
        resp.setContentType("image/png;charset=utf-8");
        resp.setHeader("Content-disposition", "attachment; filename=" + filename);

        OutputStream out = resp.getOutputStream();
        // why can't the graph module handle this?
        if (resolution == null) {
            graph.writeChartAsPNG(out, data.getGraphWidth(), data.getGraphHeight());
        } else {
            graph.writeChartAsHighResolutionPNG(out, data.getGraphWidth(), data.getGraphHeight(), resolution);
        }

    }

    @RequestMapping("/graphPie")
    public void graphPie(HttpServletRequest req, HttpServletResponse resp,
                         @RequestParam("graphDataId") String dataId,
                         @RequestParam(required = false) Integer resolution) throws GraphException, IOException {

        GraphDataSerializeToDiskHandler hndl = new GraphDataSerializeToDiskHandler(graphDir);
        GraphController gc = getGraphController(dataId, hndl, req.getUserPrincipal().getName());

        GraphDataInterface data = hndl.getGraphData(dataId);
        GraphObject graph = gc.createPieGraph(data);
        String filename = graph.getImageFileName();
        filename = filename.replaceAll("\\s", "_");
        resp.setContentType("image/png;charset=utf-8");
        resp.setHeader("Content-disposition", "attachment; filename=" + filename);

        OutputStream out = resp.getOutputStream();
        // why can't the graph module handle this?
        if (resolution == null) {
            graph.writeChartAsPNG(out, data.getGraphWidth(), data.getGraphHeight());
        } else {
            graph.writeChartAsHighResolutionPNG(out, data.getGraphWidth(), data.getGraphHeight(), resolution);
        }
    }

    @RequestMapping("/detailsQuery")
    public
    @ResponseBody
    DataSourceDetails detailsQuery(WebRequest request, @RequestParam("dsId") JdbcOeDataSource ds,
                                   @RequestParam(value = "firstrecord", defaultValue = "0") long firstRecord,
                                   @RequestParam(value = "pagesize", defaultValue = "200") long pageSize)
            throws ErrorMessageException, OeDataSourceException, OeDataSourceAccessException {

        List<Filter> filters = new Filters().getFilters(request.getParameterMap(), ds, null, 0, null, 0);
        List<Dimension> results = ControllerUtils.getResultDimensionsByIds(ds, request.getParameterValues("results"));
        List<Dimension>
                accumulations =
                ControllerUtils.getAccumulationsByIds(ds, request.getParameterValues("accumId"));

        final List<OrderByFilter> sorts = new ArrayList<OrderByFilter>();
        try {
            sorts.addAll(Sorters.getSorters(request.getParameterMap()));
        } catch (Exception e) {
            log.warn("Unable to get sorters, using default ordering");
        }

        String clientTimezone = null;
        String timezoneEnabledString = messageSource.getMessage(
                TIMEZONE_ENABLED, "false");
        if (timezoneEnabledString.equalsIgnoreCase("true")) {
            clientTimezone = ControllerUtils
                    .getRequestTimezoneAsHourMinuteString(request);
        }
        return new DetailsQuery().performDetailsQuery(ds, results, accumulations, filters, sorts, false,
                                                      clientTimezone,
                                                      firstRecord, pageSize, true);
    }

    private int getCalWeekStartDay(Map<String, ResolutionHandler> resolutionHandlers) {
        ResolutionHandler handler = resolutionHandlers.get("weekly");
        int startDay;
        if (handler == null || !(handler instanceof PgSqlWeeklyHandler)) {
            switch (Integer.parseInt(
                    messageSource.getMessage("epidemiological.day.start", Integer.toString(DEFAULT_WEEK_STARTDAY)))) {
                case 0:
                    startDay = Calendar.SUNDAY;
                    break;
                case 2:
                    startDay = Calendar.TUESDAY;
                    break;
                case 3:
                    startDay = Calendar.WEDNESDAY;
                    break;
                case 4:
                    startDay = Calendar.THURSDAY;
                    break;
                case 5:
                    startDay = Calendar.FRIDAY;
                    break;
                case 6:
                    startDay = Calendar.SATURDAY;
                    break;
                default:
                    startDay = Calendar.MONDAY;
                    break;
            }
            return startDay;
        }
        return ((PgSqlWeeklyHandler) handler).getCalWeekStartDay();
    }

    private int getWeekStartDay(Map<String, ResolutionHandler> resolutionHandlers) {
        ResolutionHandler handler = resolutionHandlers.get("weekly");
        if (handler == null || !(handler instanceof PgSqlWeeklyHandler)) {
            return Integer.parseInt(
                    messageSource.getMessage("epidemiological.day.start", Integer.toString(DEFAULT_WEEK_STARTDAY)));
        }
        return ((PgSqlWeeklyHandler) handler).getWeekStartDay();
    }


    /**
     * Extracts AccumPoint from a Collection of <code>records</code> where
     *
     * @param principal, used for logging
     */
    private List<AccumPoint> extractAccumulationPoints(String principal, DataSeriesSource ds,
                                                       final Collection<Record> records,
                                                       Date startDate, Date endDate, List<Dimension> accumulations,
                                                       final GroupingImpl group,
                                                       Map<String, ResolutionHandler> resolutionHandlers) {
        log.info(LogStatements.TIME_SERIES.getLoggingStmt() + principal);
        int startDayCal = getCalWeekStartDay(resolutionHandlers);
        int startDay = getWeekStartDay(resolutionHandlers);

        String resolution = group.getResolution();
        final String groupId = group.getId();
        int zeroFillInterval = intervalMap.keySet().contains(resolution) ? intervalMap.get(resolution) : -1;

        GroupingDimension grpdim = ds.getGroupingDimension(group.getId());
        if (zeroFillInterval != -1 && (grpdim.getSqlType() == FieldType.DATE
                                       || grpdim.getSqlType() == FieldType.DATE_TIME)) {
            ArrayList<AccumPoint> fullVector = new ArrayList<AccumPoint>(records.size());
            //create zero points for each accumulation
            Map<String, Number> zeroes = new HashMap<String, Number>();
            for (Dimension accumulation : accumulations) {
                zeroes.put(accumulation.getId(), 0);
            }
            if (records.size() > 0) {
                final Calendar cal = new GregorianCalendar();
                cal.setTime(startDate);
                Iterator<Record> recordsIterator = records.iterator();
                Record currRecord = (recordsIterator.hasNext()) ? recordsIterator.next() : null;

                //currently iterates over data incrementing cal by the resolution (weekly, daily etc)
                for (int i = 1; !cal.getTime().after(endDate); i++) {
                    // if (DAILY.equalsIgnoreCase(resolution)) {
                    boolean addRecord = false;
                    if (currRecord != null) {
                        // 2013/03/25, SCC, GGF, There are some weird edge cases with selecting data ranges that span
                        // the EDT/EST cross-overs.  Sometimes the "filter" will have the "23:00" and the data will have
                        // "00:00".  So, since we only care about the "date" anyway, clear out any subordinate fields.

                        // Database record date (set hour, minute, second, millisec to 0)
                        final Calendar rowValue = Calendar.getInstance();
                        rowValue.setTime((Date) currRecord.getValue(groupId));
                        rowValue.set(Calendar.HOUR_OF_DAY, 0);
                        rowValue.set(Calendar.MINUTE, 0);
                        rowValue.set(Calendar.SECOND, 0);
                        rowValue.set(Calendar.MILLISECOND, 0);

                        // looping variable date (set hour, minute, second, millisec to 0)
                        final Calendar calValue = Calendar.getInstance();
                        calValue.setTime(cal.getTime());
                        calValue.set(Calendar.HOUR_OF_DAY, 0);
                        calValue.set(Calendar.MINUTE, 0);
                        calValue.set(Calendar.SECOND, 0);
                        calValue.set(Calendar.MILLISECOND, 0);

                        if (resolution.equalsIgnoreCase(DAILY) && rowValue.equals(calValue)) {
                            addRecord = true;
                        } else {
                            Calendar currRecCalendar = new GregorianCalendar();
                            currRecCalendar.setTime((Date) currRecord.getValue(groupId));
                            if (resolution.equalsIgnoreCase(WEEKLY)) {
                                if (PgSqlDateHelper.getYear(startDay, cal) == PgSqlDateHelper
                                        .getYear(startDay, currRecCalendar) &&
                                    PgSqlDateHelper.getWeekOfYear(startDay, cal) == PgSqlDateHelper
                                            .getWeekOfYear(startDay, currRecCalendar)) {
                                    addRecord = true;
                                }
                            } else if (resolution.equalsIgnoreCase(MONTHLY)) {
                                if (cal.get(Calendar.YEAR) == currRecCalendar.get(Calendar.YEAR) &&
                                    cal.get(Calendar.MONTH) == currRecCalendar.get(Calendar.MONTH)) {
                                    addRecord = true;
                                }
                            }
                        }
                        if (addRecord) {
                            //if the current record matches the date put it in
                            fullVector.add(createAccumulationPoint(currRecord, accumulations));
                            currRecord = (recordsIterator.hasNext()) ? recordsIterator.next() : null;
                        }
                    }
                    if (!addRecord) {
                        //add a zero fill
                        Map<String, Dimension> m = new HashMap<String, Dimension>();
                        m.put(groupId, ds.getResultDimension(groupId));
                        HashMap<String, Object> map = new HashMap<String, Object>();
                        map.put(groupId, cal.getTime());
                        Record r = new QueryRecord(m, map);
                        fullVector.add(new AccumPointImpl(zeroes, r));
                    }
                    if (resolution.equalsIgnoreCase(WEEKLY)) {
                        // add 7 days if current date falls on week start date
                        if (i != 1) {
                            cal.add(Calendar.WEEK_OF_YEAR, 1);
                        } else {
                            cal.add(Calendar.DAY_OF_YEAR, 1);
                            while (cal.get(Calendar.DAY_OF_WEEK) != startDayCal) {
                                cal.add(Calendar.DAY_OF_YEAR, 1);
                            }
                        }
                    } else {
                        // reset the date each time to account for +month oddness
                        cal.setTime(startDate);
                        // increment the interval
                        cal.add(zeroFillInterval, 1 * i);
                    }
                }

            }
            return fullVector;
        } else {
            //pretty sure this is raw non filled
            List<AccumPoint> rawVector = new ArrayList<AccumPoint>(records.size());
            for (Record record : records) {
                rawVector.add(createAccumulationPoint(record, accumulations));
            }
            return rawVector;
        }
    }

    private AccumPoint createAccumulationPoint(Record record, List<Dimension> accumulations) {
        Map<String, Number> values = new LinkedHashMap<String, Number>();
        for (Dimension a : accumulations) {
            Object value = record.getValue(a.getId());
            Number number = (Number) value;
            values.put(a.getId(), number);
        }
        AccumPoint accumPoint = new AccumPointImpl(values, record);
        return accumPoint;
    }

    /**
     * Returns a File Download Dialog for a file containing information in the data details grid.
     *
     * @param request  the request contains needed parameters: the 'results' headers that appear in the grid
     * @param response the response object for this request
     */
    @RequestMapping("/exportGridToFile")
    public void exportGridToFile(@RequestParam("dsId") JdbcOeDataSource ds,
                                 ServletWebRequest request, HttpServletResponse response)
            throws ErrorMessageException, IOException {

        TimeZone timezone = ControllerUtils.getRequestTimezone(request);
        response.setContentType("application/json;charset=utf-8");

        final List<Filter> filters = new Filters().getFilters(request.getParameterMap(), ds, null, 0, null, 0);
        final List<Dimension> results =
                ControllerUtils.getResultDimensionsByIds(ds, request.getParameterValues("results"));

        final List<String> columnHeaders = new ArrayList<String>();
        for (final Dimension result : results) {
            if (result.getDisplayName() != null) {
                columnHeaders.add(result.getDisplayName());
            } else {
                columnHeaders.add(messageSource.getDataSourceMessage(result.getId(), ds));
            }
        }

        final List<Dimension> accumulations =
                ControllerUtils.getAccumulationsByIds(ds, request.getParameterValues("accumId"));

        final List<OrderByFilter> sorts = new ArrayList<OrderByFilter>();
        try {
            sorts.addAll(Sorters.getSorters(request.getParameterMap()));
        } catch (Exception e) {
            log.warn("Unable to get sorters, using default ordering");
        }

        String clientTimezone = null;
        String timezoneEnabledString = messageSource.getMessage(TIMEZONE_ENABLED, "false");
        if (timezoneEnabledString.equalsIgnoreCase("true")) {
            clientTimezone = ControllerUtils.getRequestTimezoneAsHourMinuteString(request);
        }
        Collection<Record> points =
                new DetailsQuery().performDetailsQuery(ds, results, accumulations, filters, sorts, false,
                                                       clientTimezone);
        // Translate accumulation int to bool if renderIntToBool set to true
        // if accumulation value is null ==> false else true
        String renderIntToBool = request.getParameter("renderIntToBool");
        if(renderIntToBool != null && renderIntToBool.equalsIgnoreCase("true")){
            for (Record point : points) {
                Map<String, Object> vals = point.getValues();
                for(Dimension accum : accumulations){
                    Object accumVal = vals.get(accum.getId());
                    vals.put(accum.getId(), accumVal != null);
                }
            }
        }
        
        response.setContentType("text/csv;charset=utf-8");

        String filename =
                messageSource.getDataSourceMessage("panel.details.export.file", ds) + "-" + new DateTime()
                        .toString("yyyyMMdd'T'HHmmss") + ".csv";
        response.setHeader("Content-disposition", "attachment; filename=" + filename);

        // Cache-Control = cache and Pragma = cache enable IE to download files over SSL.
        response.setHeader("Cache-Control", "cache");
        response.setHeader("Pragma", "cache");

        // See http://johnculviner.com/jquery-file-download-plugin-for-ajax-like-feature-rich-file-downloads/
        Cookie fileDownloadCookie = new Cookie("fileDownload", "true");
        fileDownloadCookie.setPath("/");
        response.addCookie(fileDownloadCookie);

        FileExportUtil.exportGridToCSV(response.getWriter(), columnHeaders.toArray(new String[columnHeaders.size()]),
                                       points, timezone);
    }

    private String appendUrlParameter(String url, String param, String value) {
        StringBuilder sb = new StringBuilder(url);
        if (url.contains("?")) {
            sb.append('&');
        } else {
            sb.append('?');
        }

        URLCodec codec = new URLCodec();

        try {
            sb.append(codec.encode(param));
        } catch (EncoderException e) {
            log.error("Exception encoding URL param " + param, e);
        }

        try {
            sb.append('=').append(codec.encode(value));
        } catch (EncoderException e) {
            log.error("Exception encoding URL value " + value, e);
        }

        return sb.toString();
    }

    /**
     * Get a new GraphContoller instance with sane metadata
     */
    private GraphController getGraphController(String graphDataId, GraphDataHandlerInterface graphDataHandler,
                                               String userId) {
        final String graphFont = messageSource.getMessage("graph.font", "Arial");

        GraphController graphController = new GraphController(graphDataId, graphDataHandler, userId) {

            // TODO graph module needs to be totally rewritten
            private void setGraphMetaData(Map<String, Object> graphMetaData) {
                graphMetaData.put(GraphSource.GRAPH_FONT, new Font(graphFont, Font.BOLD, 14));
                graphMetaData.put(GraphSource.GRAPH_Y_AXIS_FONT, new Font(graphFont, Font.BOLD, 12));
                graphMetaData.put(GraphSource.GRAPH_Y_AXIS_LABEL_FONT, new Font(graphFont, Font.BOLD, 12));
                graphMetaData.put(GraphSource.GRAPH_X_AXIS_FONT, new Font(graphFont, Font.PLAIN, 11));
                graphMetaData.put(GraphSource.GRAPH_X_AXIS_LABEL_FONT, new Font(graphFont, Font.PLAIN, 12));
                graphMetaData.put(GraphSource.LEGEND_FONT, new Font(graphFont, Font.PLAIN, 12));
            }

            @Override
            public void setTimeSeriesGraphMetaData(GraphDataInterface graphData, Double yAxisMin, Double yAxisMax,
                                                   double maxCount, Map<String, Object> graphMetaData) {

                super.setTimeSeriesGraphMetaData(graphData, yAxisMin, yAxisMax, maxCount, graphMetaData);
                setGraphMetaData(graphMetaData);
            }

            @Override
            public void setBarGraphMetaData(GraphDataInterface graphData, boolean stackGraph,
                                            Map<String, Object> graphMetaData) {

                super.setBarGraphMetaData(graphData, stackGraph, graphMetaData);
                setGraphMetaData(graphMetaData);
            }

            @Override
            public void setPieGraphMetaData(GraphDataInterface graphData, Map<String, Object> graphMetaData,
                                            List<PointInterface> points) {
                super.setPieGraphMetaData(graphData, graphMetaData, points);
                setGraphMetaData(graphMetaData);
            }
        };
        Map<String, String> translationMap = graphController.getTranslationMap();
        translationMap.put("Normal", messageSource.getMessage("graph.normal"));
        translationMap.put("Warning", messageSource.getMessage("graph.warning"));
        translationMap.put("Alert", messageSource.getMessage("graph.alert"));
        graphController.setTranslationMap(translationMap);

        return graphController;
    }

    /**
     * Used to add all of the given filters to the JavaScript callback.  This is intended to fix the issue when the user
     * selects more than 1 item in a multi-select filter box.  It will also handle converting dates into their numeric
     * format for later parsing on the backend.
     *
     * @param javaScript   The string builder that contains the JavaScript callback information.
     * @param filters      The filters to add.
     * @param ignoredField Sometimes, we want to ignore adding a certain filter to the callback.
     */
    private static void addJavaScriptFilters(final StringBuilder javaScript, final Collection<Filter> filters,
                                             final String ignoredField) {
        if ((filters != null) && (!filters.isEmpty())) {
            for (final Filter filter : filters) {
                if ((filter != null) && (filter instanceof FieldFilter)) {
                    final FieldFilter fieldFilter = (FieldFilter) filter;
                    final String id = fieldFilter.getFilterId();
                    final List<Object> arguments = fieldFilter.getArguments();

                    if ((id != null) && (!id.equals(ignoredField)) && (arguments != null) && (!arguments.isEmpty())) {
                        if (arguments.size() == 1) {
                            final Object value = arguments.get(0);
                            if (fieldFilter instanceof LteqFilter) {
                                javaScript.append(",").append(id).append("_end:'").append(convertFilter(value))
                                        .append("'");
                            } else if (fieldFilter instanceof GteqFilter) {
                                javaScript.append(",").append(id).append("_start:'").append(convertFilter(value))
                                        .append("'");
                            } else {
                                javaScript.append(",").append(id).append(":'").append(convertFilter(value)).append("'");
                            }
                        } else {
                            javaScript.append(",").append(id).append(":[");
                            for (int loopIndex = 0; loopIndex < arguments.size(); loopIndex++) {
                                javaScript.append(loopIndex == 0 ? "" : ",").append("'")
                                        .append(convertFilter(arguments.get(loopIndex))).append("'");
                            }
                            javaScript.append("]");
                        }
                    }
                }
            }
        }
    }

    /**
     * Used to convert the given dimension value into a Javascript-safe function call value.
     *
     * <p> In the event that the object is a <code>java.util.Date</code>, the numeric will be returned.
     *
     * <p> If <code>null</code> is given, then an empty string is returned.
     *
     * @param value The dimension value to be converted.
     * @return The safe string representation of the given value.
     */
    private static String convertFilter(final Object value) {
        // http://en.wikipedia.org/wiki/Percent-encoding
        //     All Reserved Chars:    ! # $ & ' ( ) * + , / : ; = ? @ [ ]
        //     Others Handled:        % < > ` ~ ^ | { } . - " \ _

        // If a literal _ or % (the single char and variable char wild card symbols in PostgreSQL)
        // are desired in the filter criteria, then they need to be backslash escaped by the user.

        // Yes, some need lots of backslashes due to various levels of decoding between PostgreSQL, Java, Javascript

        if (value == null) {
            return "";
        } else if (value instanceof Date) {
            return String.valueOf(((Date) value).getTime());
        } else {
            return String.valueOf(value)
                    .replaceAll("%", "%25") // To fix query filters: %fever%
                    .replaceAll("\\\\", "\\\\\\\\") // To fix chart groupings: This is a sad face :\
                    .replaceAll("'", "\\\\'") // To fix chart groupings: Prince George's
                    .replaceAll("\"", "&quot;") // To fix chart groupings: Bob said "his quote".
                    .replaceAll(" ", "%20");
        }
    }

    private static StringBuilder initJsCall(DataSeriesSource dss, Collection<String> dimIds, String accumId, String groupId, List<Filter> filters) {
        StringBuilder jsCall = new StringBuilder();
        jsCall.append("javascript:OE.report.datasource.showDetails({");
        jsCall.append("dsId:'").append(dss.getClass().getName()).append("'");
        // specify results
        jsCall.append(",results:[").append(StringUtils.collectionToDelimitedString(dimIds, ",", "'", "'")).append(']');
        // specify accumId
        jsCall.append(",accumId:'").append(accumId).append("'");
        addJavaScriptFilters(jsCall, filters, groupId);
        return jsCall;
    }
}
