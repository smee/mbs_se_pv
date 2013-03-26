(function (EntropyChart, $, baseUrl, selector, plantId, n, undefined){

var margin = {top: 200, right: 100, bottom: 10, left: 200},
    width = height = 400;
var x = d3.scale.ordinal().rangeBands([0, width]),
    c = d3.scale.linear().domain([0,1,3]).range(["green","yellow","red"]);

var svg = d3.select(selector)
            .append("svg")
              .attr("width", width + margin.left + margin.right)
              .attr("height", height + margin.top + margin.bottom);
var g = svg.append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

d3.json(baseUrl+"/data/"+plantId+"/entropy-bulk.json?n="+n, function(json) {
    var matrix = [],
        nodes = json.nodes,
        n = nodes.length,
        date = json.date;

    // Compute index per node.
    nodes.forEach(function(node, i) {
        node.index = i;
        matrix[i] = d3.range(n).map(function(j) { return {x: j, y: i, z: 0}; });
    });

    // Convert links to matrix; count character occurrences.
    json.links.forEach(function(link) {
        matrix[link.source][link.target].z = link.value;
//        matrix[link.target][link.source].z = link.value;
    });
    // The default sort order.
//    x.domain(d3.range(n));
    // sort by group
    x.domain(d3.range(n).sort(function(a, b) { return nodes[a].group - nodes[b].group; }));

    g.append("rect")
        .attr("class", "background")
        .attr("width", width)
        .attr("height", height);

    svg.append("text")
       .attr("x", 20)
       .attr("y", 20)
       .attr("class","datelabel")
       .text(date)
       .style("font-weight", "bold")
       .style("font-size", "large");

    g.selectAll("text.problabel").data(nodes)
       .enter()
       .append("text")
         .attr("class","problabel")
         .attr("x",width+x.rangeBand())
         .attr("y", function(d,i){return x(i)+x.rangeBand()/2;})
         .attr("dy", ".32em")
         .text(function(d){return (d.probability*100).toFixed(0)+"%%";});
    
    var rows = g.selectAll(".row")
        .data(matrix);
        
    var row = rows.enter().append("g")
          .attr("class", "row")
          .attr("transform", function(d, i) { return "translate(0," + x(i) + ")"; })
          .each(drawrow);
    
    row.append("line")
        .attr("x2", width);

    row.append("text")
        .attr("x", -6)
        .attr("y", x.rangeBand() / 2)
        .attr("dy", ".32em")
        .attr("text-anchor", "end")
        .attr("class","matrixlabel")
        .text(function(d, i) { return nodes[i].name; });

    var column = g.selectAll(".column")
        .data(matrix)
        .enter().append("g")
          .attr("class", "column")
          .attr("transform", function(d, i) { return "translate(" + x(i) + ")rotate(-90)"; });

    column.append("line")
        .attr("x1", -width);

    column.append("text")
        .attr("x", 6)
        .attr("y", x.rangeBand() / 2)
        .attr("dy", ".32em")
        .attr("text-anchor", "start")
        .attr("class","matrixlabel")
        .text(function(d, i) { return nodes[i].name; });

    function drawrow(row) {
        var cell = d3.select(this).selectAll(".cell")
            .data(row.filter(function(d) { return d.z; }))
            .enter().append("g")
              .attr("class","cell");
        cell.append("rect")
              .attr("x", function(d) { return x(d.x); })
              .attr("width", x.rangeBand())
              .attr("height", x.rangeBand())
              .style("fill", function(d) { return c(matrix[d.x][d.y].z);});//return nodes[d.x].group == nodes[d.y].group ? c(nodes[d.x].group) : null;})
        cell.append("text")
              .text(function(d, i) { return d.z.toFixed(2); })
              .attr("x", function(d) { return x(d.x)+2; })
              .attr("y",x.rangeBand()/2+2)
              .attr("class","cellLabel");
        cell.on("mouseover", mouseover)
            .on("mouseout", mouseout);

    }

    function mouseover(p) {
        d3.selectAll(".row text.matrixlabel").classed("active", function(d, i) { return i == p.y; });
        d3.selectAll(".column text.matrixlabel").classed("active", function(d, i) { return i == p.x; });
        d3.selectAll("text.cellLabel").classed("active", function(d, i) { return d.x==p.x && d.y == p.y; });
        d3.selectAll("text.problabel").classed("active", function(d, i) { return d.index==p.y; });
        $('#entropyText').text("Fehlerwahrscheinlichkeit von \""+nodes[p.y].name+"\": "+(nodes[p.y].probability * 100).toFixed(1)+"%%");
        //''+nodes[p.x].name+' vs. '+nodes[p.y].name+': '+p.z);
    }

    function mouseout() {
        d3.selectAll("text").classed("active", false);
    }
    
    
    EntropyChart.redraw = function(json){
        json.links.forEach(function(link) {
            matrix[link.source][link.target].z = link.value;
            matrix[link.target][link.source].z = link.value;
        });
        for(var i=0;i<nodes.length;i++)
        	nodes[i].probability=json.nodes[i].probability;
        
    	var rows = svg.selectAll(".row").data(matrix);    	
    	rows.selectAll('.cell rect').style("fill", function(d) { return c(matrix[d.x][d.y].z);});
    	rows.selectAll('.cell text.cellLabel').text(function(d) { return matrix[d.x][d.y].z.toFixed(2); });
    	svg.select(".datelabel").text(json.date);
    	svg.selectAll("text.problabel").data(nodes).text(function(d){return (d.probability*100).toFixed(0)+"%%";});
    };    

});

}( window.EntropyChart = window.EntropyChart || {}, jQuery, "%s", "%s", "%s", "%d"));