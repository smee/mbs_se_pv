(function (Charting, $, baseUrl, plantId, undefined){
    String.prototype.hashCode = function(){
        // from http://werxltd.com/wp/2010/05/13/javascript-implementation-of-javas-string-hashcode-method/
        var hash = 0, i, char;
        if (this.length == 0) return hash;
        for (i = 0; i < this.length; i++) {
            char = this.charCodeAt(i);
            hash = ((hash<<5)-hash)+char;
            hash = hash & hash; // Convert to 32bit integer
        }
        return hash;
    };
    // hide loading indicator faster
    $.blockUI.defaults.fadeOut = 0;
    $.blockUI.defaults.fadeIn = 0;
    $.blockUI.defaults.message = "<h1>Bitte warten, Daten werden geladen...</h1>";
        
    // hide loading indicator if loading an image does not work
    $('#chart-image').error(function(){ 
        this.unblock();
    });
    
    function readParameters(){
    	// load all values from form fields (text, checkbox, select)
        var params = $('form').values();
        // overwrite dates
        var startDate = $('#startDate').DatePickerGetDate(false);
        var endDate = $('#endDate').DatePickerGetDate(false);
        params.startDate = startDate;
        params.endDate = endDate;
        // add array of selected series names        
        var selectedSeries = $.map($('#series-tree').dynatree('getSelectedNodes'), function(node) { return node.data.series; });
        // there could be duplicates
        var uniqueSeries = [];
        $.each(selectedSeries, function(i,el){
        	if($.inArray(el, uniqueSeries) === -1) uniqueSeries.push(el);
        });
        params.selectedSeries = uniqueSeries;
        return params;
    }
    
    function createLink(baseUrl, plantId, params){
        var series = params.selectedSeries.join('|');
        var dates = dygraphFunctions.formatDate(params.startDate) + '-' + dygraphFunctions.formatDate(params.endDate);
        var v = params.visType;

        var link = baseUrl + '/series-of/' + plantId + '/' + series + '/' + dates + '/' + v + '?width=' + params.width + '&height=' + params.height;
        if(v == 'changepoints.png'){
            link += '&rank='+params.rank
                   +'&zero='+params.zero
                   +'&negative='+params.negative
                   +'&confidence='+params.confidence
                   +'&max-level='+params.maxLevel
                   +'&maintenance='+params.maintenance;
        }else if (v == 'entropy.json'){
            link += '&bins=' + params.bins
                 + '&min-hist=' + params.minHist
                 + '&max-hist=' + params.maxHist
                 + '&days=' + params.days
                 + '&skip-missing=' + params.skipMissing
                 + '&sensor=' + params.sensor;
        }else if(v == 'entropy-bulk.json'){
        	link += '&adhoc='
        		 + JSON.stringify(
        				 {'n': parseInt(params.days),
           				  'ids': params.selectedSeries,
           				  'min-hist': parseFloat(params.minHist),
           				  'max-hist':  parseFloat(params.maxHist),
           				  'bins': parseInt(params.bins),
           				  'skip-missing?': params.skipMissing,
           				  'threshold':1.3});
        }
        return link;
    }
    function findNearest(y, points){
    	var currentMin=1e9, currentName=points[0].name;
    	for(var i=0;i<points.length;i++){
    		var diff=Math.abs(y-points[i].canvasy);
    		if(diff<currentMin){
    			currentMin = diff;
    			currentName = points[i].name;
    		}
    	} 
    	return currentName;
    		
    }
    function createCBFunction(chartDiv,paramsOverwrite,detailChartId){
        /*
		 * XXX dirty hack: create a new chart div and return function that can
		 * be used as a click handler in a dygraph really ugly, many things hard
		 * coded
		 */
        chartDiv.append($("<div id='"+detailChartId+"'/>"));
        
        return function(e, x, points){
            var params=readParameters();
            $.extend(params,paramsOverwrite);
            
            if(params.visType == 'dygraph-ratios.json'){
            	params.selectedSeries[1]=findNearest(e.offsetY, points);
            }
            var startDate=new Date(x);
            var endDate=new Date(x);
            startDate.addDays(-10);
            endDate.addDays(3);                                                                  
            params.startDate=startDate;
            params.endDate=endDate;
            
            dygraphFunctions.createChart({id: detailChartId, 
                link: createLink(baseUrl, plantId, params), 
                params: params,
                onError: function(){ chartDiv.unblock(); }},
                function(settings, response){ 
                    settings.clickCallback = createCBFunction(chartDiv,{visType: 'dygraph.json'},'dygraph-raw-series');
                });                                                                  
          };
    }
    // handler for the main button
    $('%s').click(
		function(event) {
				event.preventDefault();
				var params = readParameters();
				// no series selected?
				if (params.selectedSeries.length == 0)
					return false;

				var visType = params.visType;
				var link = createLink(baseUrl, plantId, params);
				var chartDiv = $('#current-chart');
				chartDiv.empty();
				chartDiv.block();

				var selectionHash = params.selectedSeries.join('|').hashCode();
				var chartId = 'dygraph-chart-' + selectionHash;

				if (visType.slice(-4).toLowerCase() == '.png') { // static image
					chartDiv.append($("<img id='chart-image' src=''/>"));
					// show chart, load dynamically
					$('#chart-image').attr('src', link).load(function() {
						chartDiv.unblock();
					});
				} else {
					if (visType == 'entropy.json') {
						if(!params.sensor){
							chartDiv.append("<div class='alert'>Bitte wählen Sie aus, welcher Messwert mit den selektierten Werten verglichen werden soll.</div>");
							chartDiv.unblock();
							return false; 
						}
						chartDiv.append($("<div id='" + chartId + "'/>"));
						
						dygraphFunctions.createChart({
							id : chartId,
							link : link,
							params : params,
							onError : function() {
								chartDiv.unblock();
							}
						}, function(settings, response) {
							chartDiv.unblock();
							settings.stepPlot = true;
							settings.stackedGraph= true;
							settings.clickCallback = createCBFunction(chartDiv, {visType: 'dygraph-ratios.json', selectedSeries: [response.numerator]}, 'dygraph-chart-entropy-ratios'+selectionHash);
						});
					} else if (visType== 'entropy-bulk.json') {
						chartDiv.append($("<div id='" + chartId + "' class='widget'/>"));
						
						EntropyChart.createMatrix(baseUrl,link,"#"+chartId,plantId,function(){chartDiv.unblock();});
					}else{
						chartDiv.append($("<div id='" + chartId + "'/>"));
						dygraphFunctions.createChart({
							id : chartId,
							link : link,
							params : params,
							onError : function() {
								chartDiv.unblock();
							}
						}, function(settings, response) {
							chartDiv.unblock();
						});
					}
				}
				return false;
			});
    
}( window.Charting = window.Charting || {}, jQuery, "%s", "%s"));




