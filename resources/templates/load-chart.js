(function (Charting, $, baseUrl, id, undefined){
	// hide loading indicator faster
	$.blockUI.defaults.fadeOut = 0;
	$.blockUI.defaults.fadeIn = 0;
	$.blockUI.defaults.message = "<h1>Bitte warten, Daten werden geladen...</h1>"
		
	// hide loading indicator if loading an image does not work
	$('#chart-image').error(function(){ 
		this.unblock();
	});
	function formatDate(date){
		var m1 = date.getMonth() + 1;
		var d1 = date.getDate();
		return '' + date.getFullYear() + (m1 < 10 ? '0' + m1 : m1) + (d1 < 10 ? '0' + d1 : d1);		
	}
	function readParameters(){
		var startDate = $('#start-date').DatePickerGetDate(false);
		var endDate = $('#end-date').DatePickerGetDate(false);
		
		return{
			// selected date interval: yyyyMMdd-yyyyMMdd
			interval : formatDate(startDate) + '-' + formatDate(endDate),
			// list of selected time series
			selectedSeries : $.map($('#series-tree').dynatree('getSelectedNodes'), function(node) { return node.data.series; }),
			visType : $("#chart-type").val(),
			width : $('#chart-width').val(),
			height : $('#chart-height').val(),
			rank : $('input#rank').is(':checked'),
			zero : $('input#zero').is(':checked'),
			negative : $('input#negative').is(':checked'),
			confidence : $('input#confidence').val(),
			maxLevel : $('input#max-level').val(),
			maintainance : $('input#maintainance').is(':checked'),
			bins : $('input#bins').val(),
			minHist : $('input#min-hist').val(),
			maxHist : $('input#max-hist').val(),
			days : $('input#days').val(),
			threshold : $('input#threshold').val()
		};
	}
	
	function createLink(baseUrl, id, params){
		var series = params.selectedSeries.join('-');
		var dates = params.interval;
		var v = params.visType;
		if(v == 'interactive-map')
			return baseUrl + '/tiles/' + id + '/' + series + '/' + dates + '/{x}/{y}/{z}';
		else {
			var link = baseUrl + '/series-of/' + id + '/' + series + '/' + dates + '/' + v + '?width=' + params.width + '&height=' + params.height;
			if(v == 'changepoints.png'){
				link += '&rank='+params.rank
				       +'&zero='+params.zero
				       +'&negative='+params.negative
				       +'&confidence='+params.confidence
				       +'&max-level='+params.maxLevel
				       +'&maintainance='+maintainance;
			}else if (v == 'entropy.json'){
				link += '&bins=' + params.bins
				     + '&min-hist=' + params.minHist
				     + '&max-hist=' + params.maxHist
				     + '&days=' + params.days
				     + '&threshold=' + params.threshold;
			}
			return link;
		}
	}
	// handler for the main button
	$('%s').click(
			function(event) {
					event.preventDefault();
					var params = readParameters();
					// no series selected?
					if (params.selectedSeries.length == 0)
						return false;
					
					var visType=params.visType;					
					var link = createLink(baseUrl, id, params);
					var chartDiv = $('#current-chart');
					chartDiv.empty();
					
					chartDiv.block();

					if (visType == 'interactive-map') {
						ensure({
							js : "/js/chart/leaflet.js",
							css : "/css/chart/leaflet.css"
						}, function() {
							chartDiv.append($("<div id='interactive-map'/>"));
							var map = L.map('interactive-map').setView([ 51.505, -0.09 ], 2);
							L.tileLayer(link, {
								attribution : 'done by me :)',
								noWrap : true,
								maxZoom : 10
							}).addTo(map);
							chartDiv.unblock();
						});
					}else if (visType == 'entropy.json'){
						ensure({
							js : [ baseUrl+"/js/chart/dygraph-combined.js", baseUrl+"/js/chart/dygraph-functions.js"],
							css : []
						}, function() {							
							var chartId = 'dygraph-chart-entropy';
							chartDiv.append($("<div id='"+chartId+"'/>"));
							var detailChartId = 'dygraph-chart-entropy-ratios';
							chartDiv.append($("<div id='"+detailChartId+"'/>"));
							
							dygraphFunctions.createChart({id: chartId, 
														  link: link, 
														  params: params, 
														  onLoad: function(settings, response){ 
															  chartDiv.unblock();
															  var denominator = response.denominator;
															  var numerator = response.numerator;
															  
															  settings.clickCallback = function(e, x, points){
																  var params=readParameters();
																  params.visType='dygraph-ratios.json';
																  
																  var startDate=new Date(x);
																  var endDate=new Date(x);
																  startDate.addDays(-10);
																  endDate.addDays(3);																  
																  params.interval=formatDate(startDate) + '-' + formatDate(endDate),
																  params.selectedSeries=[numerator, denominator];
																  params.valueRange=[params.minHist, params.maxHist];
																  dygraphFunctions.createChart({id: detailChartId, link: createLink(baseUrl, id, params), params: params})																  
															  }
														  },
														  onError: function(){ chartDiv.unblock(); }});							
							});
					} else if (visType.indexOf('.json')==visType.length-5){
						ensure({
							js : [ baseUrl+"/js/chart/dygraph-combined.js", baseUrl+"/js/chart/dygraph-functions.js"],
							css : []
						}, function() {							
							var chartId = 'dygraph-chart';
							chartDiv.append($("<div id='"+chartId+"'/>"));
							dygraphFunctions.createChart({id: chartId, 
								  link: link, 
								  params: params, 
								  onLoad: function(settings, response){ chartDiv.unblock(); },
								  onError: function(){ chartDiv.unblock(); }});							
						});
					}else { //static image
						chartDiv.append($("<img id='chart-image' src=''/>"));
						// show chart
						$('#chart-image').attr('src', link).load(function() {
							chartDiv.unblock();
						});
					}
					return false;				

			});
	
}( window.Charting = window.Charting || {}, jQuery, "%s", "%s"));




