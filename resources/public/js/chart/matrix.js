(function(EntropyChart, $, undefined) {

	var cs = EntropyChart.redrawFns = EntropyChart.redrawFns || {};

	// redraw all charts on the current page by calling each redraw function
	EntropyChart.redrawAll = function() {
		for (f in EntropyChart.redrawFns)
			EntropyChart.redrawFns[f]();
	};
	// set all dropdowns to the given date string, redraw all matrices
	EntropyChart.selectByText = function(s) {
		var selects = $("select.matrixDateSelector");
		for ( var i = 0; i < selects.length; i++) {
			var options = selects[i];
			inner: for ( var j = 0; j < options.length; j++) {
				if (options[j].text == s) {
					options.selectedIndex = j;
					break;
				}
			}
		}
		EntropyChart.redrawAll();
	};

	EntropyChart.createMatrix = function(baseUrl, dataUrl, selector, plantId,afterLoadCallback) {
		var margin = {
			top : 200,
			right : 100,
			bottom : 10,
			left : 200
		}, width = height = 400;
		var //x = d3.scale.ordinal().rangeBands([0, width]),
		c = d3.scale.linear().domain([-3,-1, 0, 1, 3 ]).range(["red", "yellow", "green", "yellow", "red" ]);

		var maindiv = d3.select(selector);

		var svg = maindiv.append("svg")
		                   .attr("width", width + margin.left + margin.right)
		                   .attr("height", height + margin.top + margin.bottom);
		var g = svg.append("g")
		             .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
		var tooltip = maindiv.append("div")
		                       .attr("class", "input-prepend");
		tooltip.append("span")
		         .attr("class", "add-on")
		         .text("Zustand am");

		//var slider = tooltip.append("input")
		//               .attr("type", "number")
		//               .attr("min",0)
		//               .attr("value",0);
		var slider = tooltip.append("select");
		var summary = maindiv.append("div");

		function populate(day) {
			var matrix = [], n = day.probabilities.length;

			for ( var i = 0; i < n; i++) {
				var es = day.entropies[i];
				matrix[i] = d3.range(n).map(function(j) {
					return {
						x : j,
						y : i,
						z : es[j]==null? undefined:es[j]//0 would be interpreted as false, can't just say "es[j] || undefined"
					};
				});
			}
			return {
				matrix : matrix,
				probabilities : day.probabilities,
				date : day.date,
				since: day.since
			};
		}

		d3.json(dataUrl, function(json) {
			var names = json.names, 
			      ids = json.ids, 
			        n = names.length, 
			     days = json.days;

			var data = populate(days[days.length - 1]);

			var x = d3.scale
			          .ordinal()
			          .domain(d3.range(n))
			          .rangeBands([ 0, Math.min(width, n * 40) ]);
			
			var xlen = x.rangeBand() * n;
			
			svg.attr("width", xlen + margin.left + margin.right)
			   .attr("height", xlen + margin.top + margin.bottom);

			// date slider/dropdown
			slider.on("change", redraw)
			      .on("keyup", redraw)
			      .attr("class", "matrixDateSelector")
			      .selectAll("option")
			        .data(days.map(function(d) { return d.date; }))
			        .enter()
			          .append("option")
			            .text(function(d) { return d; });
			slider.node().selectedIndex = days.length - 1;

			g.append("rect")
			  .attr("class", "background")
			  .attr("width", xlen)
			  .attr("height", xlen);

			g.append("text")
			   .attr("x", 6)
			   .attr("y", xlen + x.rangeBand())
			   .attr("dx", ".32em")
			   .attr("transform", "translate(" + x(names.length) + ")rotate(-90)")
			   .text("Gesamtwahrscheinlichkeit");

			g.append("text")
			   .attr("x", 6)
			   .attr("y", xlen + 70)
			   .attr("dx", ".32em")
			   .attr("transform", "translate(" + x(names.length) + ")rotate(-90)")
			   .text("Fehler seit");
			
            redraw();
            

			var df = d3.time.format("%d.%m.%Y");

			function parseAndIncrementDateString(s, numberOfDaysToAdd) {
				var date = df.parse(s);
				date.setDate(date.getDate() + numberOfDaysToAdd);
				return df(date);
			}

			function loadDetailChart(d, i) {
				var elem = d3.select(this);
				var params = {
					startDate : parseAndIncrementDateString(data.date, - (data.since[i]+1)),
					endDate : data.date,
					run : true
				};
				if (elem.classed("matrixlabel")) { // clicked on label
					params.visType = "dygraph.json";
					params.selectedSeries = [];
					d.forEach(function(entry) {
						params.selectedSeries.push(ids[entry.x]);
					});
					params.highlightSeries = names[d[0].y];
				} else { // clicked on cell		      
					params.visType = "dygraph-ratios.json";
					params.selectedSeries = [ ids[d.x], ids[d.y] ];
				}
				window.open(baseUrl + '/series-of/' + plantId + '?params=' + JSON.stringify(params));
			}

			function mouseover(p) {
				svg.selectAll(".row text.matrixlabel")
				   .classed("active", function(d, i) { return i == p.y; });
				svg.selectAll(".column text")
				   .classed("active", function(d, i) { return i == p.x; });
				svg.selectAll("text.cellLabel")
				   .classed("active", function(d, i) { return d.x == p.x && d.y == p.y; });
				svg.selectAll("text.problabel")
				   .classed("active", function(d, i) { return i == p.y; });
				summary.text("Wahrscheinlichkeit einer Verhaltensänderung von \"" + names[p.y] + "\": " + (data.probabilities[p.y] * 100).toFixed(1) + "%");
			}

			function mouseout() {
				svg.selectAll("text").classed("active", false);
			}
			function drawrow(row) {		
				var cell = d3.select(this)
				             .selectAll("g.cell")
				             .data(row.filter(function(d) { return d.z != undefined;}))//may be zero which should not be interpreted as false
				thisrow=d3.select(this);
				
			    var newCellContent = cell.enter()
			             .append("g")
				           .attr("class", "cell")
				           .on("click", loadDetailChart)
				           .on("mouseover", mouseover)
					       .on("mouseout", mouseout)
		        newCellContent.append("rect")
				           .attr("x", function(d) { return x(d.x);})
				           .attr("width", x.rangeBand())
				           .attr("height", x.rangeBand());
				newCellContent.append("text")
				         .attr("x", function(d) { return x(d.x) + 2; })
				         .attr("y", x.rangeBand() / 2 + 2)
				         .attr("class", "cellLabel");
				
				cell.select("rect").style("fill", function(d) { return c(d.z); })
				cell.select("text")
				    .text(function(d, i) {
					    if (!isNaN(d.z))
    						return d.z.toFixed(2);
					    else
 						    return d.z;})
			}
			
			function redraw() {
				var n = +slider.node().value || slider.node().selectedIndex;
				if (!days[n])
					return;

				data = populate(days[n]);
				

				// date label top left
				var dateLabel = svg.selectAll("text.datelabel")
				                   .data([data.date]);
				
				dateLabel.enter()
				         .append("text")
				           .attr("x", 20)
				           .attr("y", 20)
				           .attr("class", "datelabel")
				           .style("font-weight", "bold")
				           .style("font-size", "large");
		        dateLabel.text(function(d){return d;});
				// probabilities
				var probLabels = g.selectAll("text.problabel").data(data.probabilities)
				  
				probLabels.enter()
				          .append("text")
				            .attr("class", "problabel")
				            .attr("x", xlen + x.rangeBand())
					        .attr("y", function(d, i) {	return x(i) + x.rangeBand() / 2; })
					        .attr("dy", ".32em");
				
			    probLabels.text(function(d) {	return (d * 100).toFixed(0) + "%"; });
				
				// since
				var sinceLabels = g.selectAll("text.sinceLabel").data(data.since);
				sinceLabels.enter()
				           .append("text")
				             .attr("class", "sinceLabel")
				             .attr("x", xlen + 60)
				             .attr("y", function(d, i) { return x(i) + x.rangeBand() / 2; })
				             .attr("dy", ".32em");
				sinceLabels.text(function(d) { if (d>0) return d + " Tage"; else return "";})
					

				// rows of cells
				var rows = g.selectAll("g.row").data(data.matrix);
				var row = rows.enter()
				              .append("g")
				                .attr("class", "row")
				                .attr("transform", function(d, i) { return "translate(0," + x(i) + ")"; })

				row.append("line").attr("x2", xlen);

				row.append("text")
				   .attr("x", -6)
				   .attr("y", x.rangeBand() / 2)
				   .attr("dy", ".32em")
				   .attr("text-anchor", "end")
				   .attr("class", "matrixlabel")
			       .text(function(d, i) { return names[i]; })
			       .on("click", loadDetailChart);

				rows.each(drawrow);
				
				// column labels
				var column = g.selectAll(".column")
				              .data(data.matrix)
				              .enter()
				                .append("g")
				                  .attr("class", "column")
				                  .attr("transform", function(d, i) { return "translate(" + x(i) + ")rotate(-90)";});

				column.append("line").attr("x1", -xlen);

				column.append("text")
				      .attr("x", 6)
				      .attr("y", x.rangeBand() / 2)
				      .attr("dy", ".32em")
				      .attr("text-anchor", "start")
				      .text(function(d, i) { return names[i]; });
			}
			// store reference to the redraw function of this matrix plot. XXX potential memory leak!
			cs[selector] = redraw;
			if(afterLoadCallback)
				afterLoadCallback(this);
		});
	};
}(window.EntropyChart = window.EntropyChart || {}, jQuery, undefined));