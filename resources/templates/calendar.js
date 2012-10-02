(function (selector,dataUrl){

var margin = {top: 10, right: 10, bottom: 10, left: 10},
    width = 460 - margin.right - margin.left, // width
    height = 56 - margin.top - margin.bottom, // height
    cellSize = 7; // cell size

var day = function(d){
	return (d.getDay()+6)%%7;
},
    week = d3.time.format("%%W"),
    percent = d3.format(".1%%"),
    format = d3.time.format("%%d.%%m.%%Y");

var svg = d3.select(selector).selectAll("svg")
    .data(d3.range(2007, 2013))
  .enter().append("svg")
    .attr("width", width + margin.right + margin.left)
    .attr("height", height + margin.top + margin.bottom)
    .attr("class", "RdYlGn")
  .append("g")
    .attr("transform", "translate(" + (margin.left + (width - cellSize * 53) / 2) + "," + (margin.top + (height - cellSize * 7) / 2) + ")");

svg.append("text")
    .attr("transform", "translate(-6," + cellSize * 3.5 + ")rotate(-90)")
    .attr("text-anchor", "middle")
    .text(String);

var rect = svg.selectAll("rect.day")
    .data(function(d) { return d3.time.days(new Date(d, 0, 1), new Date(d + 1, 0, 1)); })
  .enter().append("rect")
    .attr("class", "day")
    .attr("width", cellSize)
    .attr("height", cellSize)
    .attr("x", function(d) { return week(d) * cellSize; })
    .attr("y", function(d) { return day(d) * cellSize; })
    .datum(format);

rect.append("title")
    .text(function(d) { return d; });

svg.selectAll("path.month")
    .data(function(d) { return d3.time.months(new Date(d, 0, 1), new Date(d + 1, 0, 1)); })
  .enter().append("path")
    .attr("class", "month")
    .attr("d", monthPath);


d3.csv(dataUrl, function(csv) {
  var data = d3.nest()
      .key(function(d) { return d.date; })
	  .rollup(function(d){ return d[0].num; })
      .map(csv);
	  
  var color = d3.scale.quantize()
    .domain([0, 240*1440])
    .range(d3.range(9));
	
  rect.filter(function(d) { return d in data; })
      .attr("class", function(d) { return "day q"+color(data[d])+"-9"; })
    .select("title")
      .text(function(d) { return d + ": " + percent(data[d]/(240*1440)); });
});

function monthPath(t0) {
  var t1 = new Date(t0.getFullYear(), t0.getMonth() + 1, 0),
      d0 = +day(t0), w0 = +week(t0),
      d1 = +day(t1), w1 = +week(t1);
  return "M" + (w0 + 1) * cellSize + "," + d0 * cellSize
      + "H" + w0 * cellSize + "V" + 7 * cellSize
      + "H" + w1 * cellSize + "V" + (d1 + 1) * cellSize
      + "H" + (w1 + 1) * cellSize + "V" + 0
      + "H" + (w0 + 1) * cellSize + "Z";
}
})("%s", "%s")