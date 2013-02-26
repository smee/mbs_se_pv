(function (Charting, $, baseUrl, id, undefined){

	// hide loading indicator if loading an image does not work
	$('#chart-image').error(function(){ 
		$('#chart-image').hideLoading();
	});
	
	function readParameters(){
		var start = $('#start-date').DatePickerGetDate(false);
		var end = $('#end-date').DatePickerGetDate(false);
		var m1 = start.getMonth() + 1;
		var m2 = end.getMonth() + 1;
		var d1 = start.getDate();
		var d2 = end.getDate();
		var startDate = '' + start.getFullYear() + (m1 < 10 ? '0' + m1 : m1) + (d1 < 10 ? '0' + d1 : d1);
		var endDate = '' + end.getFullYear() + (m2 < 10 ? '0' + m2 : m2) + (d2 < 10 ? '0' + d2 : d2);
		
		return{ 
			// selected date interval: yyyyMMdd-yyyyMMdd
			interval : startDate + '-' + endDate,
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
					
					chartDiv.showLoading();

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
							chartDiv.hideLoading();
						});

					} else if (visType == 'dygraph.json'){
						ensure({
							js : [ baseUrl+"/js/chart/dygraph-combined.js", baseUrl+"/js/chart/dygraph-functions.js"],
							css : []
						}, function() {							
							var chartId = 'dygraph-chart';
							chartDiv.append($("<div id='"+chartId+"'/>"));
							dygraphFunctions.createChart(chartId, link, params, function(settings, response){ chartDiv.hideLoading(); });							
						});
					} else if (visType == 'entropy.json'){
						ensure({
							js : [ baseUrl+"/js/chart/dygraph-combined.js", baseUrl+"/js/chart/dygraph-functions.js"],
							css : []
						}, function() {							
							var chartId = 'dygraph-chart-entropy';
							chartDiv.append($("<div id='"+chartId+"'/>"));
							dygraphFunctions.createChart(chartId, link, params, function(settings, response){ chartDiv.hideLoading(); });							
						});
					}else { //static image
						chartDiv.append($("<img id='chart-image' src=''/>"));
						// show chart
						$('#chart-image').attr('src', link).load(function() {
							chartDiv.hideLoading();
						});
					}
					return false;				

			});
	
}( window.Charting = window.Charting || {}, jQuery, "%s", "%s"));




