$('%s').click(
		function(event) {
			(function(baseUrl, id) {
				event.preventDefault();
				// create selected date interval: yyyyMMdd-yyyyMMdd
				var start = $('#start-date').DatePickerGetDate(false);
				var end = $('#end-date').DatePickerGetDate(false);
				var m1 = start.getMonth() + 1;
				var m2 = end.getMonth() + 1;
				var d1 = start.getDate();
				var d2 = end.getDate();
				var startDate = '' + start.getFullYear() + (m1 < 10 ? '0' + m1 : m1) + (d1 < 10 ? '0' + d1 : d1);
				var endDate = '' + end.getFullYear() + (m2 < 10 ? '0' + m2 : m2) + (d2 < 10 ? '0' + d2 : d2);
				var interval = startDate + '-' + endDate;
				// create list of selected time series
				var selectedSeries = $.map($('#series-tree').dynatree('getSelectedNodes'), function(node) {
					return node.data.series;
				});
				// do not fetch a chart without any selected series
				if (selectedSeries.length < 1)
					return false;
				// find selected chart type
				var visType = $("#chart-type").val();
				// remove old contents
				var chartDiv = $('#current-chart');
				var width = $('#chart-width').val();
				var height = $('#chart-height').val();
				chartDiv.empty();

				$('#current-chart').showLoading();

				if (visType == 'interactive-map') {
					ensure({
						js : "/js/chart/leaflet.js",
						css : "/css/chart/leaflet.css"
					}, function() {
						chartDiv.append($("<div id='interactive-map'/>"));
						var map = L.map('interactive-map').setView([ 51.505, -0.09 ], 2);
						L.tileLayer(baseUrl + '/tiles/' + id + '/' + selectedSeries.join('-') + '/' + interval + '/{x}/{y}/{z}', {
							attribution : 'done by me :)',
							noWrap : true,
							maxZoom : 10
						}).addTo(map);
						$('#current-chart').hideLoading();
					});

				} else if (visType == 'dygraph'){
					ensure({
						js : [ baseUrl+"/js/chart/dygraph-combined.js", baseUrl+"/js/chart/dygraph-functions.js"],
						css : []
					}, function() {
						Date.ext.locales.de = {
								a: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
								A: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
								b: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
								B: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
								c: '%%a %%d %%b %%Y %%T %%Z',
								p: ['AM', 'PM'],
								P: ['am', 'pm'],
								x: '%%d.%%m.%%y',
								X: '%%T'
							};
						Date.prototype.locale = 'de';
						chartDiv.append($("<div id='dygraph-chart'/>"));
						var jsonURL = baseUrl + '/series-of/' + id + '/' + selectedSeries.join('-') + '/' + interval + '/data-dyson.json?width=' + width;
						// load chart data as json
						$.getJSON(jsonURL, function(response){
							$('#current-chart').hideLoading();
							// create date instances from unix timestamps
							var data = response.data;
							for(var i=0;i<data.length;i++) { 
								data[i][0] = new Date(data[i][0]); 
							}; 
							dygraphChart = new Dygraph(
									// containing div
									$('#dygraph-chart')[0],
									response.data,
									{ width: width,
									  height: height,
									  labels: response.labels,
									  customBars: true,
									  showRoller: true,
									  labelsKMB :true,
									  labelsSeparateLines: true,
									  highlightSeriesOpts: {
										  strokeWidth: 2,
										  strokeBorderWidth: 1,
										  highlightCircleSize: 5,
									  },
									  interactionModel : {
										  'mousedown' : downV3,
									      'mousemove' : moveV3,
									      'mouseup' : upV3,
									      'click' : clickV3,
									      'dblclick' : dblClickV3,
									      'mousewheel' : scrollV3
									  }
									});
							chartDiv.append($("<input type='button' class='btn btn-info' value='Position wiederherstellen' onclick='restorePositioning(dygraphChart)'>"));
						});
					});
				} else { //static image
					chartDiv.append($("<img id='chart-image' src=''/>"));
					// create link
					var link = baseUrl + '/series-of/' + id + '/' + selectedSeries.join('-') + '/' + interval + '/' + visType + '.png?width='
							+ width + '&height=' + height;
					if(visType == 'changepoints'){
						link += '&rank='+$('input#rank').is(':checked')
						       +'&zero='+$('input#zero').is(':checked')
						       +'&negative='+$('input#negative').is(':checked')
						       +'&confidence='+$('input#confidence').val()
						       +'&max-level='+$('input#max-level').val()
						       +'&maintainance='+$('input#maintainance').is(':checked');
					}else if (visType == 'entropy'){
						link += '&bins=' + $('input#bins').val()
						     +  '&min-hist=' + $('input#min-hist').val()
						     +  '&max-hist=' + $('input#max-hist').val()
						     +  '&days=' + $('input#days').val()
						     +  '&threshold=' + $('input#threshold').val();
					}
					// show chart
					$('#chart-image').attr('src', link).load(function() {
						$('#current-chart').hideLoading();
					});
				}
				return false;
			})("%s", "%s");

		});
