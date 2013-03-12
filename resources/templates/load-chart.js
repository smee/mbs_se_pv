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
    $.blockUI.defaults.message = "<h1>Bitte warten, Daten werden geladen...</h1>"
        
    // hide loading indicator if loading an image does not work
    $('#chart-image').error(function(){ 
        this.unblock();
    });
    
    function readParameters(){
        var params = {};
        // load all values from form fields (text, checkbox, select)
        $('form input').each(function(){params[$(this).attr('name')]=$(this).val()});
        $('form input[type="checkbox"]').each(function(){params[$(this).attr('name')]=$(this).is(':checked')});
        $('form select').each(function(){params[$(this).attr('id')]=$(this).val()});
        // overwrite dates
        var startDate = $('#startDate').DatePickerGetDate(false);
        var endDate = $('#endDate').DatePickerGetDate(false);
        params.startDate = startDate;
        params.endDate = endDate;
        // add array of selected series names
        params.selectedSeries = $.map($('#series-tree').dynatree('getSelectedNodes'), function(node) { return node.data.series; });
        return params;
    }
    
    function createLink(baseUrl, plantId, params){
        var series = params.selectedSeries.join('-');
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
        }else if (v == 'entropy.json' || v == 'entropy-bulk.json'){
            link += '&bins=' + params.bins
                 + '&min-hist=' + params.minHist
                 + '&max-hist=' + params.maxHist
                 + '&days=' + params.days
                 + '&threshold=' + params.threshold
                 + '&sensor=' + params.sensor;
        }
        return link;
    }
    
    function createCBFunction(chartDiv,visType,detailChartId){
        /*
		 * XXX dirty hack: create a new chart div and return function that can
		 * be used as a click handler in a dygraph really ugly, many things hard
		 * coded
		 */
        chartDiv.append($("<div id='"+detailChartId+"'/>"));
        
        return function(e, x, point){
            var params=readParameters();
            params.visType=visType;
            var startDate=new Date(x);
            var endDate=new Date(x);
            startDate.addDays(-10);
            endDate.addDays(3);                                                                  
            params.startDate=startDate;
            params.endDate=endDate;
              
            if(visType.slice(0,7)=='entropy'){
            	  params.valueRange=[params.minHist, params.maxHist];
            }
            console.log('clicked on chart, loading new chart',detailChartId, params);
            dygraphFunctions.createChart({id: detailChartId, 
                link: createLink(baseUrl, plantId, params), 
                params: params,
                onError: function(){ chartDiv.unblock(); }},
                function(settings, response){ 
                    settings.clickCallback = createCBFunction(chartDiv,'dygraph.json','dygraph-raw-series');
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

				var concattedSelection = params.selectedSeries.reduce(function(all, s) {
					return all.concat(s);
				});
				var chartId = 'dygraph-chart-entropy-' + concattedSelection.hashCode();

				if (visType.slice(-4).toLowerCase() == '.png') { // static image
					chartDiv.append($("<img id='chart-image' src=''/>"));
					// show chart, load dynamically
					$('#chart-image').attr('src', link).load(function() {
						chartDiv.unblock();
					});
				} else {
					if (visType == 'entropy.json' || visType == 'entropy-bulk.json') {
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
							settings.stackedGraph= (visType=='entropy-bulk.json');
//							settings.fillGraph= (visType=='entropy-bulk.json');
							settings.clickCallback = createCBFunction(chartDiv, 'dygraph-ratios.json', 'dygraph-chart-entropy-ratios');
							console.log(settings,response);
						});
					} else if (visType.slice(-5) == '.json') {
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




